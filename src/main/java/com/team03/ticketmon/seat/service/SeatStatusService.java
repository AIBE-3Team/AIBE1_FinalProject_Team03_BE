package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.seat.config.SeatProperties;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import com.team03.ticketmon.seat.exception.SeatReservationException;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis Hash를 활용한 좌석 상태 관리 서비스
 * ✅ 수정사항:
 * - Cache-Aside 패턴 추가 (자동 캐시 초기화)
 * - SeatCacheInitService 의존성 추가
 * - 분산 락을 활용한 원자적 좌석 선점 처리
 * - 사용자별 좌석 선점 개수 제한 (최대 6개) 추가
 * - 현재 테스트 환경임을 고려하여 선점 개수 제한을 2개로 설정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStatusService {

    private final RedissonClient redissonClient;
    private final SeatStatusEventPublisher eventPublisher;
    private final SeatCacheInitService seatCacheInitService; // ✅ 추가된 필드
    private final SeatProperties seatProperties;
    private final ConcertSeatRepository concertSeatRepository;

    // Redis 키 패턴
    private static final String SEAT_STATUS_KEY_PREFIX = RedisKeyGenerator.SEAT_STATUS_KEY_PREFIX;
    private static final String SEAT_LOCK_KEY_PREFIX = RedisKeyGenerator.SEAT_LOCK_KEY_PREFIX;
    private static final String SEAT_TTL_KEY_PREFIX = RedisKeyGenerator.SEAT_TTL_KEY_PREFIX;

    // 업데이트 시간 추적을 위한 키
    private static final String SEAT_LAST_UPDATE_KEY_PREFIX = RedisKeyGenerator.SEAT_LAST_UPDATE_KEY_PREFIX;

    /**
     * ✅ 수정된 전체 좌석 상태 조회 - Cache-Aside 패턴 적용
     */
    public Map<Long, SeatStatus> getAllSeatStatus(Long concertId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        Map<String, SeatStatus> rawMap = seatMap.readAllMap();

        // ✅ Cache Miss 시 자동 초기화
        if (rawMap.isEmpty()) {
            log.info("좌석 캐시가 비어있음. 자동 초기화 시작: concertId={}", concertId);
            try {
                seatCacheInitService.initializeSeatCacheFromDB(concertId);
                rawMap = seatMap.readAllMap(); // 재조회
                log.info("좌석 캐시 자동 초기화 완료: concertId={}, 좌석수={}", concertId, rawMap.size());
            } catch (Exception e) {
                log.error("좌석 캐시 자동 초기화 실패: concertId={}", concertId, e);
            }
        }

        return rawMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Long.valueOf(entry.getKey()),
                        Map.Entry::getValue
                ));
    }

    /**
     * ✅ 수정된 개별 좌석 상태 조회 - Cache-Aside 패턴 적용
     */
    public Optional<SeatStatus> getSeatStatus(Long concertId, Long concertSeatId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        SeatStatus status = seatMap.get(concertSeatId.toString());

        // ✅ 캐시에 없고 전체 캐시도 비어있으면 초기화 시도
        if (status == null && seatMap.size() == 0) {
            log.info("개별 좌석 조회 시 캐시 비어있음. 초기화 시도: concertId={}, concertSeatId={}", concertId, concertSeatId);
            try {
                seatCacheInitService.initializeSeatCacheFromDB(concertId);
                status = seatMap.get(concertSeatId.toString()); // 재조회
            } catch (Exception e) {
                log.error("개별 좌석 조회 시 캐시 초기화 실패: concertId={}, concertSeatId={}", concertId, concertSeatId, e);
            }
        }

        return Optional.ofNullable(status);
    }

    /**
     * 좌석 상태 업데이트 (기본 버전)
     * - Redis Hash에 좌석 상태 저장
     * - 실시간 이벤트 발행으로 다른 사용자들에게 변경사항 알림
     */
    public void updateSeatStatus(SeatStatus seatStatus) {
        String key = SEAT_STATUS_KEY_PREFIX + seatStatus.getConcertId();
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        // 1. Redis에 좌석 상태 저장
        seatMap.put(seatStatus.getSeatId().toString(), seatStatus);

        // 2. 마지막 업데이트 시간 갱신
        updateLastUpdateTime(seatStatus.getConcertId());

        // 3. 실시간 이벤트 발행 (실패해도 좌석 상태 저장에는 영향 없음)
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
     */
    private void createSeatTTLKey(Long concertId, Long concertSeatId) {
        try {
            String ttlKey = SEAT_TTL_KEY_PREFIX + concertId + ":" + concertSeatId;
            RBucket<String> bucket = redissonClient.getBucket(ttlKey);

            bucket.set("reserved", seatProperties.getReservation().getTtlMinutes(), TimeUnit.MINUTES);
            log.debug("좌석 TTL 키 생성: key={}, ttl={}분", ttlKey, seatProperties.getReservation().getTtlMinutes());

        } catch (Exception e) {
            log.error("좌석 TTL 키 생성 실패: concertId={}, concertSeatId={}", concertId, concertSeatId, e);
        }
    }

    /**
     * 좌석 TTL 키 삭제
     */
    private void removeSeatTTLKey(Long concertId, Long concertSeatId) {
        try {
            String ttlKey = SEAT_TTL_KEY_PREFIX + concertId + ":" + concertSeatId;
            RBucket<String> bucket = redissonClient.getBucket(ttlKey);

            boolean deleted = bucket.delete();
            if (deleted) {
                log.debug("좌석 TTL 키 삭제 완료: key={}", ttlKey);
            } else {
                log.debug("좌석 TTL 키 삭제 시도 - 키가 존재하지 않음: key={}", ttlKey);
            }

        } catch (Exception e) {
            log.error("좌석 TTL 키 삭제 실패: concertId={}, concertSeatId={}", concertId, concertSeatId, e);
        }
    }

    /**
     * ✅ 사용자별 좌석 선점 개수 검증
     * Redis에서 현재 사용자가 선점한 좌석 개수를 확인하여 최대 제한을 초과하는지 검증
     *
     * @param concertId    콘서트 ID
     * @param userId       사용자 ID
     * @param targetSeatId 새로 선점하려는 좌석 ID (동일 좌석 재선점 시 제외용)
     * @throws SeatReservationException 최대 선점 개수 초과 시
     */
    private void validateUserSeatReservationLimit(Long concertId, Long userId, Long targetSeatId) {
        List<SeatStatus> userReservedSeats = getUserReservedSeats(concertId, userId);

        // 현재 선점하려는 좌석이 이미 해당 사용자에 의해 선점된 상태라면 개수에서 제외
        // (동일 좌석 재선점의 경우)
        long currentReservationCount = userReservedSeats.stream()
                .filter(seat -> !seat.getSeatId().equals(targetSeatId))
                .count();

        int maxSeatCount = seatProperties.getReservation().getMaxSeatCount();
        if (currentReservationCount >= maxSeatCount) {
            log.warn("사용자 좌석 선점 개수 제한 초과: userId={}, concertId={}, currentCount={}, maxLimit={}",
                    userId, concertId, currentReservationCount, maxSeatCount);

            throw new SeatReservationException(
                    String.format("좌석 선점은 최대 %d개까지만 가능합니다. 현재 선점 좌석: %d개",
                            maxSeatCount, currentReservationCount)
            );
        }

        log.debug("사용자 좌석 선점 개수 검증 통과: userId={}, concertId={}, currentCount={}, maxLimit={}",
                userId, concertId, currentReservationCount, maxSeatCount);
    }

    /**
     * 좌석 임시 선점 메서드 (4개 매개변수 버전)
     * - 좌석 가용성 확인과 선점 처리를 원자적으로 수행
     * - Race Condition 방지 및 중복 예약 차단
     * - TTL 키 생성으로 자동 만료 처리 지원
     * - ✅ 사용자별 최대 6개 좌석 선점 제한 추가
     *
     * @param concertId     콘서트 ID
     * @param concertSeatId 좌석 ID (ConcertSeat ID)
     * @param userId        사용자 ID
     * @param seatInfo      좌석 정보
     * @return 선점된 좌석 상태
     * @throws SeatReservationException 좌석 선점 실패 시
     */
    @Transactional
    public SeatStatus reserveSeat(Long concertId, Long concertSeatId, Long userId, String seatInfo) {
        String lockKey = SEAT_LOCK_KEY_PREFIX + concertId + ":" + concertSeatId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 분산 락 획득 시도 (3초 대기, 10초 보유)
            boolean acquired = lock.tryLock(seatProperties.getLock().getWaitTimeSeconds(),
                    seatProperties.getLock().getLeaseTimeSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("좌석 락 획득 실패: concertId={}, concertSeatId={}, userId={}", concertId, concertSeatId, userId);
                throw new SeatReservationException("다른 사용자가 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }
            log.debug("좌석 락 획득 성공: concertId={}, concertSeatId={}, userId={}", concertId, concertSeatId, userId);

            // === 임계 구역 시작 ===

            // 1. 현재 좌석 상태 확인
            Optional<SeatStatus> currentStatus = getSeatStatus(concertId, concertSeatId);

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
                            log.info("동일 사용자의 좌석 재선점 요청: concertId={}, concertSeatId={}, userId={}",
                                    concertId, concertSeatId, userId);
                            return seat; // 기존 선점 상태 반환
                        } else {
                            throw new SeatReservationException("다른 사용자가 선점 중인 좌석입니다.");
                        }
                    } else {
                        log.info("만료된 선점 좌석 해제 후 재선점: concertId={}, concertSeatId={}",
                                concertId, concertSeatId);
                        // 만료된 선점은 아래에서 새로 선점 처리
                    }
                }
            }

            // ✅ 2. 사용자별 좌석 선점 개수 제한 검증 (새로 추가된 로직)
            validateUserSeatReservationLimit(concertId, userId, concertSeatId);

            // 3. 새로운 선점 처리 (기존 번호 2에서 3으로 변경)
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusMinutes(seatProperties.getReservation().getTtlMinutes());

            SeatStatus reserved = SeatStatus.builder()
                    .id(concertId + "-" + concertSeatId)
                    .concertId(concertId)
                    .seatId(concertSeatId)
                    .status(SeatStatusEnum.RESERVED)
                    .userId(userId)
                    .reservedAt(now)
                    .expiresAt(expiresAt)
                    .seatInfo(seatInfo)
                    .build();

            // 4. Redis에 저장 및 이벤트 발행 (기존 번호 3에서 4로 변경)
            updateSeatStatus(reserved);

            // 5. TTL 키 생성 (자동 만료 지원) (기존 번호 4에서 5로 변경)
            createSeatTTLKey(concertId, concertSeatId);

            log.info("좌석 선점 완료: concertId={}, concertSeatId={}, userId={}, expiresAt={}, seatInfo={}",
                    concertId, concertSeatId, userId, expiresAt, seatInfo);

            return reserved;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("좌석 선점 중 인터럽트 발생: concertId={}, concertSeatId={}, userId={}", concertId, concertSeatId, userId, e);
            throw new SeatReservationException("좌석 선점 처리가 중단되었습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("좌석 락 해제 완료: concertId={}, concertSeatId={}, userId={}", concertId, concertSeatId, userId);
            }
        }
    }

    /**
     * 좌석 선점 해제 (AVAILABLE로 변경)
     */
    public void releaseSeat(Long concertId, Long concertSeatId, Long userId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, concertSeatId);

        if (!currentStatus.isPresent()) {
            log.warn("존재하지 않는 좌석 해제 시도: concertId={}, concertSeatId={}, userId={}",
                    concertId, concertSeatId, userId);
            throw new SeatReservationException("존재하지 않는 좌석입니다.");
        }

        SeatStatus currentSeat = currentStatus.get();

        // 1. 좌석 상태가 RESERVED인지 확인
        if (!currentSeat.isReserved()) {
            log.warn("선점되지 않은 좌석 해제 시도: concertId={}, concertSeatId={}, userId={}, currentStatus={}",
                    concertId, concertSeatId, userId, currentSeat.getStatus());
            throw new SeatReservationException("선점되지 않은 좌석은 해제할 수 없습니다. 현재 상태: " + currentSeat.getStatus());
        }

        // 2. 해제 요청 사용자가 선점한 사용자와 일치하는지 확인
        if (!userId.equals(currentSeat.getUserId())) {
            log.warn("권한 없는 좌석 해제 시도: concertId={}, concertSeatId={}, requestUserId={}, reservedUserId={}",
                    concertId, concertSeatId, userId, currentSeat.getUserId());
            throw new SeatReservationException("다른 사용자가 선점한 좌석은 해제할 수 없습니다.");
        }

        // 3. 검증 통과 시 좌석 해제 처리
        SeatStatus updatedStatus = SeatStatus.builder()
                .id(concertId + "-" + concertSeatId)
                .concertId(concertId)
                .seatId(concertSeatId)
                .status(SeatStatusEnum.AVAILABLE)
                .userId(null)
                .reservedAt(null)
                .expiresAt(null)
                .seatInfo(currentSeat.getSeatInfo())
                .build();

        updateSeatStatus(updatedStatus);

        // 4. TTL 키 삭제 (불필요한 만료 이벤트 방지)
        removeSeatTTLKey(concertId, concertSeatId);

        log.info("좌석 선점 해제 완료: concertId={}, concertSeatId={}, userId={}", concertId, concertSeatId, userId);
    }

    /**
     * 관리자용 좌석 강제 해제 (권한 검증 없음)
     */
    public void forceReleaseSeat(Long concertId, Long concertSeatId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, concertSeatId);

        if (currentStatus.isPresent()) {
            SeatStatus currentSeat = currentStatus.get();

            SeatStatus updatedStatus = SeatStatus.builder()
                    .id(concertId + "-" + concertSeatId)
                    .concertId(concertId)
                    .seatId(concertSeatId)
                    .status(SeatStatusEnum.AVAILABLE)
                    .userId(null)
                    .reservedAt(null)
                    .expiresAt(null)
                    .seatInfo(currentSeat.getSeatInfo())
                    .build();

            updateSeatStatus(updatedStatus);
            removeSeatTTLKey(concertId, concertSeatId);

            log.info("좌석 강제 해제 완료 (관리자): concertId={}, concertSeatId={}, previousUserId={}",
                    concertId, concertSeatId, currentSeat.getUserId());
        }
    }

    /**
     * 좌석 예매 완료 처리
     * ⚠️ 사용하지 않는 메서드
     */
    public void bookSeat(Long concertId, Long concertSeatId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, concertSeatId);

        if (currentStatus.isPresent() && currentStatus.get().isReserved()) {
            SeatStatus currentSeat = currentStatus.get();

            if (currentSeat.isExpired()) {
                log.warn("만료된 선점 좌석 예매 시도: concertId={}, concertSeatId={}, userId={}",
                        concertId, concertSeatId, currentSeat.getUserId());
                throw new SeatReservationException("선점이 만료된 좌석입니다. 다시 선점해주세요.");
            }

            SeatStatus bookedStatus = SeatStatus.builder()
                    .id(concertId + "-" + concertSeatId)
                    .concertId(concertId)
                    .seatId(concertSeatId)
                    .status(SeatStatusEnum.BOOKED)
                    .userId(currentSeat.getUserId())
                    .reservedAt(currentSeat.getReservedAt())
                    .expiresAt(null)
                    .seatInfo(currentSeat.getSeatInfo())
                    .build();

            updateSeatStatus(bookedStatus);
            removeSeatTTLKey(concertId, concertSeatId);

            log.info("좌석 예매 완료: concertId={}, concertSeatId={}, userId={}",
                    concertId, concertSeatId, currentSeat.getUserId());

        } else {
            String currentState = currentStatus.isPresent() ?
                    currentStatus.get().getStatus().toString() : "NOT_FOUND";

            log.warn("예매 불가능한 좌석 상태: concertId={}, concertSeatId={}, currentState={}",
                    concertId, concertSeatId, currentState);
            throw new SeatReservationException("선점되지 않은 좌석은 예매할 수 없습니다. 현재 상태: " + currentState);
        }
    }

    /**
     * 만료된 선점 좌석들 정리 (시스템 운영용)
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

    /**
     * 콘서트 좌석의 마지막 업데이트 시간 조회
     */
    public LocalDateTime getLastUpdateTime(Long concertId) {
        try {
            String key = SEAT_LAST_UPDATE_KEY_PREFIX + concertId;
            RBucket<LocalDateTime> bucket = redissonClient.getBucket(key);
            return bucket.get();
        } catch (Exception e) {
            log.warn("마지막 업데이트 시간 조회 중 오류: concertId={}", concertId, e);
            return null;
        }
    }

    /**
     * 콘서트 좌석의 마지막 업데이트 시간 설정
     */
    private void updateLastUpdateTime(Long concertId) {
        try {
            String key = SEAT_LAST_UPDATE_KEY_PREFIX + concertId;
            RBucket<LocalDateTime> bucket = redissonClient.getBucket(key);
            bucket.set(LocalDateTime.now(), seatProperties.getReservation().getLastUpdateTtlHours(), TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("마지막 업데이트 시간 설정 중 오류: concertId={}", concertId, e);
        }
    }

    /**
     * 현재 좌석 상태 요약 조회 (폴링용)
     */
    public Map<String, Object> getCurrentSeatStatus(Long concertId) {
        try {
            Map<Long, SeatStatus> allSeats = getAllSeatStatus(concertId);

            Map<String, Long> statusCounts = allSeats.values().stream()
                    .collect(Collectors.groupingBy(
                            seat -> seat.getStatus().toString(),
                            Collectors.counting()
                    ));

            return Map.of(
                    "concertId", concertId,
                    "totalSeats", allSeats.size(),
                    "statusCounts", statusCounts,
                    "lastChecked", LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("현재 좌석 상태 조회 중 오류: concertId={}", concertId, e);
            return Map.of(
                    "concertId", concertId,
                    "error", "상태 조회 실패",
                    "lastChecked", LocalDateTime.now()
            );
        }
    }

    /**
     * 캐시 → DB 백업 기능
     * Redis 캐시에 있는 좌석 상태를 간단히 로깅만 수행
     * (DB 구조상 캐시 상태를 직접 반영할 수 없음)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> backupCacheToDatabase(Long concertId) {
        try {
            log.info("캐시 상태 확인 시작: concertId={}", concertId);
            
            // Redis에서 모든 좌석 상태 조회
            RMap<String, SeatStatus> seatMap = redissonClient.getMap(SEAT_STATUS_KEY_PREFIX + concertId);
            Collection<SeatStatus> allSeats = seatMap.readAllValues();
            
            if (allSeats.isEmpty()) {
                log.warn("확인할 캐시 데이터가 없습니다: concertId={}", concertId);
                return Map.of(
                    "message", "캐시 데이터 없음",
                    "processedSeats", 0,
                    "timestamp", LocalDateTime.now()
                );
            }
            
            // 상태별 통계 생성
            Map<SeatStatusEnum, Long> statusCounts = allSeats.stream()
                .collect(Collectors.groupingBy(
                    SeatStatus::getStatus,
                    Collectors.counting()
                ));
            
            log.info("캐시 상태 확인 완료: concertId={}, totalSeats={}, breakdown={}", 
                concertId, allSeats.size(), statusCounts);
            
            return Map.of(
                "message", "캐시 상태 확인 완료",
                "processedSeats", allSeats.size(),
                "statusBreakdown", statusCounts.entrySet().stream()
                    .collect(Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().intValue()
                    )),
                "note", "DB 구조상 캐시 상태를 직접 백업할 수 없습니다. 상태 확인만 수행됩니다.",
                "timestamp", LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("캐시 상태 확인 중 오류: concertId={}", concertId, e);
            throw new RuntimeException("상태 확인 실패: " + e.getMessage(), e);
        }
    }

    /**
     * DB 좌석 초기화 기능
     * 모든 좌석을 AVAILABLE 상태로 초기화
     */
    @Transactional
    public Map<String, Object> resetAllSeatsToAvailable(Long concertId) {
        try {
            log.info("좌석 상태 초기화 시작: concertId={}", concertId);
            
            // DB에서 모든 좌석을 AVAILABLE로 업데이트
            int updatedCount = concertSeatRepository.bulkUpdateAllSeatsToAvailable(concertId);
            
            log.info("좌석 상태 초기화 완료: concertId={}, updatedSeats={}", concertId, updatedCount);
            
            return Map.of(
                "message", "초기화 완료",
                "processedSeats", updatedCount,
                "newStatus", "AVAILABLE",
                "timestamp", LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("좌석 상태 초기화 중 오류: concertId={}", concertId, e);
            throw new RuntimeException("초기화 실패: " + e.getMessage(), e);
        }
    }
}