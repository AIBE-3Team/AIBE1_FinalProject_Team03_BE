package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import com.team03.ticketmon.seat.exception.SeatReservationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis Hash를 활용한 좌석 상태 관리 서비스
 * 키 구조: seat:status:{concertId} -> Hash(seatId -> SeatStatus)
 * 분산 락을 활용한 원자적 좌석 선점 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStatusService {

    private final RedissonClient redissonClient;
    private final SeatStatusEventPublisher eventPublisher;

    // Redis 키 패턴
    private static final String SEAT_STATUS_KEY_PREFIX = "seat:status:";
    private static final String SEAT_LOCK_KEY_PREFIX = "seat:lock:";
    private static final String SEAT_TTL_KEY_PREFIX = "seat:expire:"; // TTL 키 패턴

    // 분산 락 타임아웃 설정
    private static final long LOCK_WAIT_TIME = 3; // 락 획득 대기 시간 (초)
    private static final long LOCK_LEASE_TIME = 10; // 락 보유 시간 (초)

    // TTL 설정
    private static final long SEAT_RESERVATION_TTL_MINUTES = 5; // 좌석 선점 유지 시간 (분)

    /**
     * 특정 콘서트의 전체 좌석 상태 조회
     * ⚠️ 자동 해제 로직 임시 비활성화
     */
    public Map<Long, SeatStatus> getAllSeatStatus(Long concertId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        // String 키를 Long으로 변환하여 반환 (자동 해제 로직 제거)
        return seatMap.readAllMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Long.valueOf(entry.getKey()),
                        Map.Entry::getValue
                ));
    }

    /**
     * 특정 좌석 상태 조회
     * ⚠️ 자동 해제 로직 임시 비활성화
     */
    public Optional<SeatStatus> getSeatStatus(Long concertId, Long seatId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        SeatStatus status = seatMap.get(seatId.toString());
        return Optional.ofNullable(status);
    }

    /**
     * 좌석 상태 업데이트 (기본 버전 - TTL 로직 제거)
     * - Redis Hash에 좌석 상태 저장
     * - 실시간 이벤트 발행으로 다른 사용자들에게 변경사항 알림
     */
    public void updateSeatStatus(SeatStatus seatStatus) {
        String key = SEAT_STATUS_KEY_PREFIX + seatStatus.getConcertId();
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        // 1. Redis에 좌석 상태 저장
        seatMap.put(seatStatus.getSeatId().toString(), seatStatus);

        // 2. 실시간 이벤트 발행 (실패해도 좌석 상태 저장에는 영향 없음)
        try {
            eventPublisher.publishSeatUpdate(seatStatus);
        } catch (Exception e) {
            log.warn("좌석 상태 이벤트 발행 실패 (서비스 계속 진행): concertId={}, seatId={}",
                    seatStatus.getConcertId(), seatStatus.getSeatId(), e);
        }

        log.info("좌석 상태 업데이트: concertId={}, seatId={}, status={}",
                seatStatus.getConcertId(), seatStatus.getSeatId(), seatStatus.getStatus());
    }

    /**
     * 좌석 TTL 키 생성
     * - Redis Key Expiration Event를 위한 TTL 키 생성
     * - 좌석 선점 시간과 동일한 TTL 설정 (5분)
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     */
    private void createSeatTTLKey(Long concertId, Long seatId) {
        try {
            String ttlKey = SEAT_TTL_KEY_PREFIX + concertId + ":" + seatId;
            RBucket<String> bucket = redissonClient.getBucket(ttlKey);

            // 단순 마커 키로 사용 (값은 중요하지 않음)
            bucket.set("reserved", SEAT_RESERVATION_TTL_MINUTES, TimeUnit.MINUTES);

            log.debug("좌석 TTL 키 생성: key={}, ttl={}분", ttlKey, SEAT_RESERVATION_TTL_MINUTES);

        } catch (Exception e) {
            log.error("좌석 TTL 키 생성 실패: concertId={}, seatId={}", concertId, seatId, e);
            // TTL 키 생성 실패가 좌석 선점 자체를 막지 않도록 예외를 던지지 않음
        }
    }

    /**
     * 좌석 TTL 키 삭제
     * - 좌석 해제 시 TTL 키 정리
     * - 불필요한 만료 이벤트 방지
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     */
    private void removeSeatTTLKey(Long concertId, Long seatId) {
        try {
            String ttlKey = SEAT_TTL_KEY_PREFIX + concertId + ":" + seatId;
            RBucket<String> bucket = redissonClient.getBucket(ttlKey);

            boolean deleted = bucket.delete();

            if (deleted) {
                log.debug("좌석 TTL 키 삭제 완료: key={}", ttlKey);
            } else {
                log.debug("좌석 TTL 키 삭제 시도 - 키가 존재하지 않음: key={}", ttlKey);
            }

        } catch (Exception e) {
            log.error("좌석 TTL 키 삭제 실패: concertId={}, seatId={}", concertId, seatId, e);
            // TTL 키 삭제 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }

    /**
     * 좌석 임시 선점 (원자적 처리, 분산 락 사용)
     * - 좌석 가용성 확인과 선점 처리를 원자적으로 수행
     * - Race Condition 방지 및 중복 예약 차단
     * - TTL 키 생성으로 자동 만료 처리 지원
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param userId 사용자 ID
     * @param seatInfo 좌석 정보
     * @return 선점된 좌석 상태
     * @throws SeatReservationException 좌석 선점 실패 시
     */
    public SeatStatus reserveSeat(Long concertId, Long seatId, Long userId, String seatInfo) {
        String lockKey = SEAT_LOCK_KEY_PREFIX + concertId + ":" + seatId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 분산 락 획득 시도 (3초 대기, 10초 보유)
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("좌석 락 획득 실패 (타임아웃): concertId={}, seatId={}, userId={}",
                        concertId, seatId, userId);
                throw new SeatReservationException("좌석 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }

            log.debug("좌석 락 획득 성공: concertId={}, seatId={}, userId={}",
                    concertId, seatId, userId);

            // === 임계 구역 시작 ===

            // 1. 현재 좌석 상태 확인
            Optional<SeatStatus> currentStatus = getSeatStatus(concertId, seatId);

            if (currentStatus.isPresent()) {
                SeatStatus seat = currentStatus.get();

                // 이미 예매 완료된 좌석
                if (seat.getStatus() == SeatStatusEnum.BOOKED) {
                    throw new SeatReservationException("이미 예매 완료된 좌석입니다.");
                }

                // 현재 선점 중인 좌석 (만료 여부 확인)
                if (seat.getStatus() == SeatStatusEnum.RESERVED) {
                    if (!seat.isExpired()) {
                        // 같은 사용자의 재요청인지 확인
                        if (userId.equals(seat.getUserId())) {
                            log.info("동일 사용자의 좌석 재선점 요청: concertId={}, seatId={}, userId={}",
                                    concertId, seatId, userId);
                            return seat; // 기존 선점 상태 반환
                        } else {
                            throw new SeatReservationException("다른 사용자가 선점 중인 좌석입니다.");
                        }
                    } else {
                        log.info("만료된 선점 좌석 해제 후 재선점: concertId={}, seatId={}",
                                concertId, seatId);
                        // 만료된 선점은 아래에서 새로 선점 처리
                    }
                }
            }

            // 2. 새로운 선점 처리
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusMinutes(SEAT_RESERVATION_TTL_MINUTES); // 5분 후 만료

            SeatStatus reservedStatus = SeatStatus.builder()
                    .id(concertId + "-" + seatId)
                    .concertId(concertId)
                    .seatId(seatId)
                    .status(SeatStatusEnum.RESERVED)
                    .userId(userId)
                    .reservedAt(now)
                    .expiresAt(expiresAt)
                    .seatInfo(seatInfo)
                    .build();

            // 3. Redis에 원자적 저장
            updateSeatStatus(reservedStatus);

            // 4. TTL 키 생성 (자동 만료 처리를 위함)
            createSeatTTLKey(concertId, seatId);

            log.info("좌석 선점 완료: concertId={}, seatId={}, userId={}, expiresAt={}",
                    concertId, seatId, userId, expiresAt);

            return reservedStatus;

            // === 임계 구역 종료 ===

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("좌석 선점 중 인터럽트 발생: concertId={}, seatId={}, userId={}",
                    concertId, seatId, userId, e);
            throw new SeatReservationException("좌석 선점 처리가 중단되었습니다.");
        } catch (SeatReservationException e) {
            // 비즈니스 예외는 그대로 재throw
            throw e;
        } catch (Exception e) {
            log.error("좌석 선점 중 예기치 않은 오류 발생: concertId={}, seatId={}, userId={}",
                    concertId, seatId, userId, e);
            throw new SeatReservationException("좌석 선점 처리 중 오류가 발생했습니다.");
        } finally {
            // 락 해제 (안전한 해제)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("좌석 락 해제 완료: concertId={}, seatId={}, userId={}",
                        concertId, seatId, userId);
            }
        }
    }

    /**
     * 좌석 선점 해제 (AVAILABLE로 변경)
     * - 선점한 사용자만 해제 가능
     * - RESERVED 상태의 좌석만 해제 가능
     * - TTL 키 삭제로 불필요한 만료 이벤트 방지
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param userId 해제 요청 사용자 ID
     * @throws SeatReservationException 해제 권한이 없거나 잘못된 상태인 경우
     */
    public void releaseSeat(Long concertId, Long seatId, Long userId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, seatId);

        if (!currentStatus.isPresent()) {
            log.warn("존재하지 않는 좌석 해제 시도: concertId={}, seatId={}, userId={}",
                    concertId, seatId, userId);
            throw new SeatReservationException("존재하지 않는 좌석입니다.");
        }

        SeatStatus currentSeat = currentStatus.get();

        // 1. 좌석 상태가 RESERVED인지 확인
        if (!currentSeat.isReserved()) {
            log.warn("선점되지 않은 좌석 해제 시도: concertId={}, seatId={}, userId={}, currentStatus={}",
                    concertId, seatId, userId, currentSeat.getStatus());
            throw new SeatReservationException("선점되지 않은 좌석은 해제할 수 없습니다. 현재 상태: " + currentSeat.getStatus());
        }

        // 2. 해제 요청 사용자가 선점한 사용자와 일치하는지 확인
        if (!userId.equals(currentSeat.getUserId())) {
            log.warn("권한 없는 좌석 해제 시도: concertId={}, seatId={}, requestUserId={}, reservedUserId={}",
                    concertId, seatId, userId, currentSeat.getUserId());
            throw new SeatReservationException("다른 사용자가 선점한 좌석은 해제할 수 없습니다.");
        }

        // 3. 검증 통과 시 좌석 해제 처리
        SeatStatus updatedStatus = SeatStatus.builder()
                .id(concertId + "-" + seatId)
                .concertId(concertId)
                .seatId(seatId)
                .status(SeatStatusEnum.AVAILABLE)
                .userId(null)
                .reservedAt(null)
                .expiresAt(null)
                .seatInfo(currentSeat.getSeatInfo())
                .build();

        updateSeatStatus(updatedStatus);

        // 4. TTL 키 삭제 (불필요한 만료 이벤트 방지)
        removeSeatTTLKey(concertId, seatId);

        log.info("좌석 선점 해제 완료: concertId={}, seatId={}, userId={}", concertId, seatId, userId);
    }

    /**
     * 관리자용 좌석 강제 해제 (권한 검증 없음)
     * - 만료된 선점 정리 등 시스템 운영 목적
     * - 일반 사용자 접근 차단 필요
     * - TTL 키도 함께 삭제
     */
    public void forceReleaseSeat(Long concertId, Long seatId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, seatId);

        if (currentStatus.isPresent()) {
            SeatStatus currentSeat = currentStatus.get();

            SeatStatus updatedStatus = SeatStatus.builder()
                    .id(concertId + "-" + seatId)
                    .concertId(concertId)
                    .seatId(seatId)
                    .status(SeatStatusEnum.AVAILABLE)
                    .userId(null)
                    .reservedAt(null)
                    .expiresAt(null)
                    .seatInfo(currentSeat.getSeatInfo())
                    .build();

            updateSeatStatus(updatedStatus);

            // TTL 키 삭제 (관리자 강제 해제 시에도 TTL 키 정리)
            removeSeatTTLKey(concertId, seatId);

            log.info("좌석 강제 해제 완료 (관리자): concertId={}, seatId={}, previousUserId={}",
                    concertId, seatId, currentSeat.getUserId());
        }
    }

    /**
     * 좌석 예매 완료 처리
     * - 선점된 좌석만 예매 완료 처리 가능
     * - 비즈니스 규칙: RESERVED 상태의 좌석만 BOOKED로 전환
     * - TTL 키 삭제 (예매 완료 시 만료 처리 불필요)
     */
    public void bookSeat(Long concertId, Long seatId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, seatId);

        // 좌석 상태 존재 여부 및 선점 상태 검증
        if (currentStatus.isPresent() && currentStatus.get().isReserved()) {
            SeatStatus currentSeat = currentStatus.get();

            // 선점이 만료되었는지 추가 검증
            if (currentSeat.isExpired()) {
                log.warn("만료된 선점 좌석 예매 시도: concertId={}, seatId={}, userId={}",
                        concertId, seatId, currentSeat.getUserId());
                throw new SeatReservationException("선점이 만료된 좌석입니다. 다시 선점해주세요.");
            }

            SeatStatus bookedStatus = SeatStatus.builder()
                    .id(concertId + "-" + seatId)
                    .concertId(concertId)
                    .seatId(seatId)
                    .status(SeatStatusEnum.BOOKED)
                    .userId(currentSeat.getUserId())
                    .reservedAt(currentSeat.getReservedAt())
                    .expiresAt(null) // 예매 완료 시 만료 시간 제거
                    .seatInfo(currentSeat.getSeatInfo())
                    .build();

            updateSeatStatus(bookedStatus);

            // TTL 키 삭제 (예매 완료 시 자동 만료 처리 불필요)
            removeSeatTTLKey(concertId, seatId);

            log.info("좌석 예매 완료: concertId={}, seatId={}, userId={}",
                    concertId, seatId, currentSeat.getUserId());

        } else {
            // 좌석이 존재하지 않거나 선점 상태가 아닌 경우
            String currentState = currentStatus.isPresent() ?
                    currentStatus.get().getStatus().toString() : "NOT_FOUND";

            log.warn("예매 불가능한 좌석 상태: concertId={}, seatId={}, currentState={}",
                    concertId, seatId, currentState);
            throw new SeatReservationException("선점되지 않은 좌석은 예매할 수 없습니다. 현재 상태: " + currentState);
        }
    }

    /**
     * 만료된 선점 좌석들 정리 (시스템 운영용)
     * - 관리자 권한으로 만료된 선점 일괄 해제
     */
    public void cleanupExpiredReservations(Long concertId) {
        Map<Long, SeatStatus> allSeats = getAllSeatStatus(concertId);
        LocalDateTime now = LocalDateTime.now();

        for (SeatStatus seat : allSeats.values()) {
            if (seat.isReserved() && seat.getExpiresAt() != null && now.isAfter(seat.getExpiresAt())) {
                forceReleaseSeat(concertId, seat.getSeatId());
                log.info("만료된 선점 좌석 해제: concertId={}, seatId={}, expiredUserId={}",
                        concertId, seat.getSeatId(), seat.getUserId());
            }
        }
    }

    /**
     * 특정 사용자의 선점 좌석 조회
     */
    public List<SeatStatus> getUserReservedSeats(Long concertId, Long userId) {
        Map<Long, SeatStatus> allSeats = getAllSeatStatus(concertId);

        return allSeats.values().stream()
                .filter(seat -> seat.isReserved() && userId.equals(seat.getUserId()))
                .collect(Collectors.toList());
    }
}