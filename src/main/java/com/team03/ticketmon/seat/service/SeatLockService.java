package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import com.team03.ticketmon.seat.dto.BulkSeatLockResult;
import com.team03.ticketmon.seat.dto.SeatLockResult;
import com.team03.ticketmon.seat.exception.SeatReservationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 좌석 영구 선점 처리 서비스 (확장된 버전)
 *
 * 목적: Redis TTL 삭제 후 좌석 상태를 영구적으로 선점 상태로 변경
 *
 * 주요 기능:
 * - 단일 좌석 영구 선점/복원 (기존 기능)
 * - 다중 좌석 일괄 영구 선점/복원 (신규 기능)
 * - TTL 키 삭제하여 자동 만료 방지
 * - 권한 검증 및 상태 검증
 * - 실시간 이벤트 발행
 *
 * 사용 시나리오:
 * - 결제 진행 시: 결제 처리 중 좌석이 만료되지 않도록 보장
 * - 예매 확정 직전: 최종 확정 전 좌석을 안전하게 고정
 * - 다중 좌석 선택 시: 사용자가 선점한 모든 좌석을 한 번에 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockService {

    private final SeatStatusService seatStatusService;
    private final RedissonClient redissonClient;
    private final SeatStatusEventPublisher eventPublisher;

    // TTL 키 패턴 (SeatStatusService와 동일)
    private static final String SEAT_TTL_KEY_PREFIX = "seat:expire:";

    // ========== 기존 단일 좌석 처리 메서드들 ==========

    /**
     * 좌석을 영구 선점 상태로 변경
     *
     * 프로세스:
     * 1. 현재 좌석 상태 검증 (RESERVED 상태 확인)
     * 2. 권한 검증 (선점한 사용자와 요청 사용자 일치)
     * 3. TTL 키 삭제 (자동 만료 방지)
     * 4. 좌석 상태를 PERMANENTLY_RESERVED로 변경
     * 5. 실시간 이벤트 발행
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param userId 요청 사용자 ID
     * @return 영구 선점 처리 결과
     * @throws SeatReservationException 검증 실패 시
     */
    public SeatLockResult lockSeatPermanently(Long concertId, Long seatId, Long userId) {
        log.info("좌석 영구 선점 요청: concertId={}, seatId={}, userId={}", concertId, seatId, userId);

        LocalDateTime lockStartTime = LocalDateTime.now();

        try {
            // 1. 현재 좌석 상태 조회 및 검증
            SeatStatus currentSeat = validateSeatForLocking(concertId, seatId, userId);

            // 2. TTL 키 삭제 (자동 만료 방지)
            boolean ttlRemoved = removeSeatTTLKey(concertId, seatId);

            // 3. 좌석 상태를 영구 선점으로 변경
            SeatStatus permanentlyLockedSeat = createPermanentlyLockedSeat(currentSeat);
            seatStatusService.updateSeatStatus(permanentlyLockedSeat);

            // 4. 실시간 이벤트 발행 (다른 사용자들에게 알림)
            publishLockEvent(permanentlyLockedSeat);

            LocalDateTime lockEndTime = LocalDateTime.now();

            SeatLockResult result = SeatLockResult.builder()
                    .concertId(concertId)
                    .seatId(seatId)
                    .userId(userId)
                    .lockStartTime(lockStartTime)
                    .lockEndTime(lockEndTime)
                    .previousStatus(currentSeat.getStatus())
                    .newStatus(permanentlyLockedSeat.getStatus())
                    .ttlKeyRemoved(ttlRemoved)
                    .seatInfo(currentSeat.getSeatInfo())
                    .success(true)
                    .build();

            log.info("좌석 영구 선점 완료: {}", result.getSummary());
            return result;

        } catch (SeatReservationException e) {
            log.warn("좌석 영구 선점 실패 - 검증 오류: concertId={}, seatId={}, userId={}, message={}",
                    concertId, seatId, userId, e.getMessage());

            return SeatLockResult.builder()
                    .concertId(concertId)
                    .seatId(seatId)
                    .userId(userId)
                    .lockStartTime(lockStartTime)
                    .lockEndTime(LocalDateTime.now())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("좌석 영구 선점 중 예기치 않은 오류: concertId={}, seatId={}, userId={}",
                    concertId, seatId, userId, e);

            return SeatLockResult.builder()
                    .concertId(concertId)
                    .seatId(seatId)
                    .userId(userId)
                    .lockStartTime(lockStartTime)
                    .lockEndTime(LocalDateTime.now())
                    .success(false)
                    .errorMessage("시스템 오류: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 영구 선점된 좌석을 일반 선점 상태로 되돌리기
     *
     * 사용 시나리오:
     * - 결제 실패 시: 영구 선점을 해제하고 다시 임시 선점으로 변경
     * - 결제 취소 시: 좌석을 다시 선점 가능한 상태로 복원
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param userId 요청 사용자 ID
     * @param restoreWithTTL TTL을 다시 설정할지 여부
     * @return 복원 처리 결과
     */
    public SeatLockResult restoreSeatReservation(Long concertId, Long seatId, Long userId, boolean restoreWithTTL) {
        log.info("좌석 선점 상태 복원 요청: concertId={}, seatId={}, userId={}, withTTL={}",
                concertId, seatId, userId, restoreWithTTL);

        LocalDateTime restoreStartTime = LocalDateTime.now();

        try {
            // 1. 현재 좌석 상태 검증
            Optional<SeatStatus> currentStatus = seatStatusService.getSeatStatus(concertId, seatId);

            if (currentStatus.isEmpty()) {
                throw new SeatReservationException("존재하지 않는 좌석입니다.");
            }

            SeatStatus currentSeat = currentStatus.get();

            // 2. 권한 검증
            if (!userId.equals(currentSeat.getUserId())) {
                throw new SeatReservationException("다른 사용자의 좌석은 복원할 수 없습니다.");
            }

            // 3. 현재 상태가 영구 선점인지 확인 (BOOKED 상태는 복원 불가)
            if (currentSeat.getStatus() == SeatStatusEnum.BOOKED) {
                throw new SeatReservationException("이미 예매 완료된 좌석은 복원할 수 없습니다.");
            }

            // 4. 일반 선점 상태로 복원
            SeatStatus restoredSeat = createRestoredReservation(currentSeat, restoreWithTTL);
            seatStatusService.updateSeatStatus(restoredSeat);

            // 5. TTL 키 재생성 (옵션)
            if (restoreWithTTL) {
                createSeatTTLKey(concertId, seatId);
            }

            LocalDateTime restoreEndTime = LocalDateTime.now();

            SeatLockResult result = SeatLockResult.builder()
                    .concertId(concertId)
                    .seatId(seatId)
                    .userId(userId)
                    .lockStartTime(restoreStartTime)
                    .lockEndTime(restoreEndTime)
                    .previousStatus(currentSeat.getStatus())
                    .newStatus(restoredSeat.getStatus())
                    .ttlKeyRemoved(false) // 복원 시에는 TTL 키 생성
                    .seatInfo(currentSeat.getSeatInfo())
                    .success(true)
                    .build();

            log.info("좌석 선점 상태 복원 완료: {}", result.getSummary());
            return result;

        } catch (Exception e) {
            log.error("좌석 선점 상태 복원 중 오류: concertId={}, seatId={}, userId={}",
                    concertId, seatId, userId, e);

            return SeatLockResult.builder()
                    .concertId(concertId)
                    .seatId(seatId)
                    .userId(userId)
                    .lockStartTime(restoreStartTime)
                    .lockEndTime(LocalDateTime.now())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    // ========== 신규 다중 좌석 처리 메서드들 ==========

    /**
     * 사용자가 선점한 모든 좌석을 일괄 영구 선점 처리
     *
     * 프로세스:
     * 1. 사용자의 모든 선점 좌석 조회
     * 2. 각 좌석에 대해 영구 선점 처리 (순차 실행)
     * 3. 결과 집계 및 통계 생성
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @return 일괄 영구 선점 처리 결과
     */
    public BulkSeatLockResult lockAllUserSeatsPermanently(Long concertId, Long userId) {
        log.info("사용자 모든 좌석 일괄 영구 선점 요청: concertId={}, userId={}", concertId, userId);

        LocalDateTime bulkStartTime = LocalDateTime.now();

        try {
            // 1. 사용자의 모든 선점 좌석 조회
            List<SeatStatus> userReservedSeats = seatStatusService.getUserReservedSeats(concertId, userId);

            if (userReservedSeats.isEmpty()) {
                log.info("영구 선점할 좌석이 없음: concertId={}, userId={}", concertId, userId);
                return BulkSeatLockResult.failure(concertId, userId,
                        BulkSeatLockResult.BulkOperationType.LOCK, "선점된 좌석이 없습니다.");
            }

            log.info("일괄 영구 선점 대상 좌석 수: {} (concertId={}, userId={})",
                    userReservedSeats.size(), concertId, userId);

            // 2. 각 좌석에 대해 영구 선점 처리 (순차 실행)
            List<SeatLockResult> seatResults = new ArrayList<>();

            for (SeatStatus seat : userReservedSeats) {
                log.debug("좌석 영구 선점 처리 중: concertId={}, seatId={}, userId={}",
                        concertId, seat.getSeatId(), userId);

                SeatLockResult result = lockSeatPermanently(concertId, seat.getSeatId(), userId);
                seatResults.add(result);

                // 성공/실패 로그
                if (result.isSuccess()) {
                    log.debug("좌석 영구 선점 성공: seatId={}", seat.getSeatId());
                } else {
                    log.warn("좌석 영구 선점 실패: seatId={}, error={}",
                            seat.getSeatId(), result.getErrorMessage());
                }
            }

            // 3. 결과 집계 및 반환
            LocalDateTime bulkEndTime = LocalDateTime.now();
            BulkSeatLockResult bulkResult = BulkSeatLockResult.allSuccess(
                    concertId, userId, seatResults,
                    BulkSeatLockResult.BulkOperationType.LOCK,
                    bulkStartTime, bulkEndTime
            );

            log.info("사용자 모든 좌석 일괄 영구 선점 완료: {}", bulkResult.getSummary());
            return bulkResult;

        } catch (Exception e) {
            log.error("사용자 모든 좌석 일괄 영구 선점 중 예외 발생: concertId={}, userId={}",
                    concertId, userId, e);

            return BulkSeatLockResult.failure(concertId, userId,
                    BulkSeatLockResult.BulkOperationType.LOCK,
                    "시스템 오류: " + e.getMessage());
        }
    }

    /**
     * 사용자가 영구 선점한 모든 좌석을 일괄 상태 복원
     *
     * 프로세스:
     * 1. 사용자의 모든 영구 선점 좌석 조회 (expiresAt이 null인 RESERVED 상태)
     * 2. 각 좌석에 대해 상태 복원 처리 (순차 실행)
     * 3. 결과 집계 및 통계 생성
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @param restoreWithTTL TTL을 다시 설정할지 여부
     * @return 일괄 상태 복원 처리 결과
     */
    public BulkSeatLockResult restoreAllUserSeats(Long concertId, Long userId, boolean restoreWithTTL) {
        log.info("사용자 모든 좌석 일괄 상태 복원 요청: concertId={}, userId={}, withTTL={}",
                concertId, userId, restoreWithTTL);

        LocalDateTime bulkStartTime = LocalDateTime.now();

        try {
            // 1. 사용자의 모든 영구 선점 좌석 조회
            List<SeatStatus> userPermanentlyLockedSeats = getUserPermanentlyLockedSeats(concertId, userId);

            if (userPermanentlyLockedSeats.isEmpty()) {
                log.info("복원할 영구 선점 좌석이 없음: concertId={}, userId={}", concertId, userId);
                return BulkSeatLockResult.failure(concertId, userId,
                        BulkSeatLockResult.BulkOperationType.RESTORE, "영구 선점된 좌석이 없습니다.");
            }

            log.info("일괄 상태 복원 대상 좌석 수: {} (concertId={}, userId={})",
                    userPermanentlyLockedSeats.size(), concertId, userId);

            // 2. 각 좌석에 대해 상태 복원 처리 (순차 실행)
            List<SeatLockResult> seatResults = new ArrayList<>();

            for (SeatStatus seat : userPermanentlyLockedSeats) {
                log.debug("좌석 상태 복원 처리 중: concertId={}, seatId={}, userId={}",
                        concertId, seat.getSeatId(), userId);

                SeatLockResult result = restoreSeatReservation(concertId, seat.getSeatId(), userId, restoreWithTTL);
                seatResults.add(result);

                // 성공/실패 로그
                if (result.isSuccess()) {
                    log.debug("좌석 상태 복원 성공: seatId={}", seat.getSeatId());
                } else {
                    log.warn("좌석 상태 복원 실패: seatId={}, error={}",
                            seat.getSeatId(), result.getErrorMessage());
                }
            }

            // 3. 결과 집계 및 반환
            LocalDateTime bulkEndTime = LocalDateTime.now();
            BulkSeatLockResult bulkResult = BulkSeatLockResult.allSuccess(
                    concertId, userId, seatResults,
                    BulkSeatLockResult.BulkOperationType.RESTORE,
                    bulkStartTime, bulkEndTime
            );

            log.info("사용자 모든 좌석 일괄 상태 복원 완료: {}", bulkResult.getSummary());
            return bulkResult;

        } catch (Exception e) {
            log.error("사용자 모든 좌석 일괄 상태 복원 중 예외 발생: concertId={}, userId={}",
                    concertId, userId, e);

            return BulkSeatLockResult.failure(concertId, userId,
                    BulkSeatLockResult.BulkOperationType.RESTORE,
                    "시스템 오류: " + e.getMessage());
        }
    }

    /**
     * 사용자의 영구 선점 좌석 목록 조회
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @return 영구 선점된 좌석 목록 (expiresAt이 null인 RESERVED 상태)
     */
    public List<SeatStatus> getUserPermanentlyLockedSeats(Long concertId, Long userId) {
        List<SeatStatus> userReservedSeats = seatStatusService.getUserReservedSeats(concertId, userId);

        return userReservedSeats.stream()
                .filter(seat -> seat.isReserved() && seat.getExpiresAt() == null) // 영구 선점 조건
                .collect(java.util.stream.Collectors.toList());
    }

    // ========== 기존 공통 메서드들 (변경 없음) ==========

    /**
     * 좌석 영구 선점 가능 여부 확인 (실제 처리 없이 검증만)
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param userId 사용자 ID
     * @return 영구 선점 가능 여부 및 상세 정보
     */
    public SeatLockCheckResult checkSeatLockEligibility(Long concertId, Long seatId, Long userId) {
        try {
            SeatStatus currentSeat = validateSeatForLocking(concertId, seatId, userId);

            return SeatLockCheckResult.builder()
                    .concertId(concertId)
                    .seatId(seatId)
                    .userId(userId)
                    .eligible(true)
                    .currentStatus(currentSeat.getStatus())
                    .remainingTTL(currentSeat.getRemainingSeconds())
                    .seatInfo(currentSeat.getSeatInfo())
                    .message("영구 선점 가능")
                    .build();

        } catch (Exception e) {
            return SeatLockCheckResult.builder()
                    .concertId(concertId)
                    .seatId(seatId)
                    .userId(userId)
                    .eligible(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    /**
     * 좌석 영구 선점을 위한 검증
     */
    private SeatStatus validateSeatForLocking(Long concertId, Long seatId, Long userId) {
        // 1. 좌석 상태 존재 여부 확인
        Optional<SeatStatus> currentStatus = seatStatusService.getSeatStatus(concertId, seatId);

        if (currentStatus.isEmpty()) {
            throw new SeatReservationException("존재하지 않는 좌석입니다.");
        }

        SeatStatus currentSeat = currentStatus.get();

        // 2. 현재 상태가 RESERVED인지 확인
        if (!currentSeat.isReserved()) {
            throw new SeatReservationException(
                    String.format("선점되지 않은 좌석은 영구 선점할 수 없습니다. 현재 상태: %s",
                            currentSeat.getStatus()));
        }

        // 3. 선점 만료 확인
        if (currentSeat.isExpired()) {
            throw new SeatReservationException("만료된 선점 좌석은 영구 선점할 수 없습니다.");
        }

        // 4. 권한 검증 (선점한 사용자와 요청 사용자 일치)
        if (!userId.equals(currentSeat.getUserId())) {
            throw new SeatReservationException("다른 사용자가 선점한 좌석은 영구 선점할 수 없습니다.");
        }

        return currentSeat;
    }

    /**
     * TTL 키 삭제
     */
    private boolean removeSeatTTLKey(Long concertId, Long seatId) {
        try {
            String ttlKey = SEAT_TTL_KEY_PREFIX + concertId + ":" + seatId;
            RBucket<String> bucket = redissonClient.getBucket(ttlKey);

            boolean deleted = bucket.delete();

            if (deleted) {
                log.debug("TTL 키 삭제 성공: key={}", ttlKey);
            } else {
                log.debug("TTL 키가 존재하지 않거나 이미 만료됨: key={}", ttlKey);
            }

            return deleted;

        } catch (Exception e) {
            log.error("TTL 키 삭제 실패: concertId={}, seatId={}", concertId, seatId, e);
            return false;
        }
    }

    /**
     * TTL 키 생성 (복원 시 사용)
     */
    private void createSeatTTLKey(Long concertId, Long seatId) {
        try {
            String ttlKey = SEAT_TTL_KEY_PREFIX + concertId + ":" + seatId;
            RBucket<String> bucket = redissonClient.getBucket(ttlKey);

            // 5분 TTL로 생성
            bucket.set("reserved", 5, java.util.concurrent.TimeUnit.MINUTES);

            log.debug("TTL 키 재생성: key={}, ttl=5분", ttlKey);

        } catch (Exception e) {
            log.error("TTL 키 생성 실패: concertId={}, seatId={}", concertId, seatId, e);
        }
    }

    /**
     * 영구 선점 좌석 상태 생성
     */
    private SeatStatus createPermanentlyLockedSeat(SeatStatus currentSeat) {
        return SeatStatus.builder()
                .id(currentSeat.getId())
                .concertId(currentSeat.getConcertId())
                .seatId(currentSeat.getSeatId())
                .status(SeatStatusEnum.RESERVED) // 기존 enum 사용 (BOOKED와 구분 위해 추후 PERMANENTLY_RESERVED 고려)
                .userId(currentSeat.getUserId())
                .reservedAt(currentSeat.getReservedAt())
                .expiresAt(null) // 만료 시간 제거 (영구 선점)
                .seatInfo(currentSeat.getSeatInfo())
                .build();
    }

    /**
     * 복원된 선점 좌석 상태 생성
     */
    private SeatStatus createRestoredReservation(SeatStatus currentSeat, boolean withTTL) {
        LocalDateTime expiresAt = withTTL ?
                LocalDateTime.now().plusMinutes(5) : null;

        return SeatStatus.builder()
                .id(currentSeat.getId())
                .concertId(currentSeat.getConcertId())
                .seatId(currentSeat.getSeatId())
                .status(SeatStatusEnum.RESERVED)
                .userId(currentSeat.getUserId())
                .reservedAt(currentSeat.getReservedAt())
                .expiresAt(expiresAt)
                .seatInfo(currentSeat.getSeatInfo())
                .build();
    }

    /**
     * 영구 선점 이벤트 발행
     */
    private void publishLockEvent(SeatStatus lockedSeat) {
        try {
            eventPublisher.publishSeatUpdate(lockedSeat);
            log.debug("영구 선점 이벤트 발행 완료: concertId={}, seatId={}",
                    lockedSeat.getConcertId(), lockedSeat.getSeatId());
        } catch (Exception e) {
            log.warn("영구 선점 이벤트 발행 실패: concertId={}, seatId={}",
                    lockedSeat.getConcertId(), lockedSeat.getSeatId(), e);
        }
    }

    /**
     * 영구 선점 확인 결과 DTO (내부 클래스)
     */
    @lombok.Builder
    @lombok.Getter
    public static class SeatLockCheckResult {
        private final Long concertId;
        private final Long seatId;
        private final Long userId;
        private final boolean eligible;
        private final SeatStatusEnum currentStatus;
        private final Long remainingTTL;
        private final String seatInfo;
        private final String message;
    }
}