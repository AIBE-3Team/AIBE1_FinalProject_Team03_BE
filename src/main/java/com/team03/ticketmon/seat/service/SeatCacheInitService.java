package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 좌석 상태 캐시 초기화 서비스
 * - 콘서트 예매 시작 시 DB 데이터를 Redis로 Warm-up
 * - 대량 데이터 처리 시 배치 처리로 네트워크 호출 최소화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatCacheInitService {

    private final RedissonClient redissonClient;
    private static final String SEAT_STATUS_KEY_PREFIX = "seat:status:";

    /**
     * 특정 콘서트의 좌석 캐시 초기화 (성능 최적화 버전)
     * - 실제 운영에서는 DB에서 좌석 정보를 가져와서 Redis에 적재
     * - 현재는 테스트용 더미 데이터로 초기화
     * - 배치 처리로 네트워크 호출 최소화
     */
    public void initializeSeatCache(Long concertId, int totalSeats) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        // 기존 캐시 클리어
        seatMap.clear();

        // 로컬 HashMap에 모든 좌석 데이터 준비 (네트워크 호출 최소화)
        Map<String, SeatStatus> batchSeatData = new HashMap<>();

        // 더미 좌석 데이터 생성 및 로컬 맵에 저장
        for (int i = 1; i <= totalSeats; i++) {
            String seatInfo = generateSeatInfo(i); // A-1, A-2, B-1 등

            SeatStatus seatStatus = SeatStatus.builder()
                    .id(concertId + "-" + i)
                    .concertId(concertId)
                    .seatId((long) i)
                    .status(SeatStatusEnum.AVAILABLE)
                    .userId(null)
                    .reservedAt(null)
                    .expiresAt(null)
                    .seatInfo(seatInfo)
                    .build();

            // 로컬 맵에 누적 (Redis 호출 없음)
            batchSeatData.put(String.valueOf(i), seatStatus);
        }

        // 한 번의 Redis 호출로 모든 데이터 일괄 저장
        seatMap.putAll(batchSeatData);

        log.info("좌석 캐시 초기화 완료 (배치 처리): concertId={}, totalSeats={}, batchSize={}",
                concertId, totalSeats, batchSeatData.size());
    }

    /**
     * 실제 운영에서 사용할 DB 기반 캐시 초기화 메서드 (구조만 제공)
     * TODO: 실제 DB 연동 시 구현 필요
     */
    public void initializeSeatCacheFromDB(Long concertId) {
        log.info("DB 기반 좌석 캐시 초기화 시작: concertId={}", concertId);

        // TODO: 실제 구현 시 아래 단계 수행
        // 1. DB에서 concert_seats 테이블 조회 (concert_id 기준)
        // 2. 각 좌석의 현재 상태 확인 (tickets 테이블 확인)
        // 3. Redis Hash에 좌석 상태 배치 저장

        // 예시 구조 (배치 처리 최적화 적용):
        /*
        List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertId(concertId);
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        // 로컬 맵에 모든 좌석 상태 준비
        Map<String, SeatStatus> batchSeatData = new HashMap<>();

        for (ConcertSeat concertSeat : concertSeats) {
            // 예매 여부 확인
            boolean isBooked = ticketRepository.existsByConcertSeatId(concertSeat.getId());

            SeatStatus seatStatus = SeatStatus.builder()
                    .id(concertId + "-" + concertSeat.getSeat().getId())
                    .concertId(concertId)
                    .seatId(concertSeat.getSeat().getId())
                    .status(isBooked ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE)
                    .seatInfo(concertSeat.getSeat().getSection() + "-" + concertSeat.getSeat().getSeatNumber())
                    .build();

            // 로컬 맵에 누적
            batchSeatData.put(concertSeat.getSeat().getId().toString(), seatStatus);
        }

        // 한 번의 Redis 호출로 모든 데이터 일괄 저장
        seatMap.putAll(batchSeatData);

        log.info("DB 기반 좌석 캐시 초기화 완료 (배치 처리): concertId={}, batchSize={}",
                concertId, batchSeatData.size());
        */
    }

    /**
     * 좌석 정보 문자열 생성 (더미 데이터용)
     * 1~50: A구역, 51~100: B구역, 101~150: C구역
     */
    private String generateSeatInfo(int seatNumber) {
        String section;
        int seatInSection;

        if (seatNumber <= 50) {
            section = "A";
            seatInSection = seatNumber;
        } else if (seatNumber <= 100) {
            section = "B";
            seatInSection = seatNumber - 50;
        } else {
            section = "C";
            seatInSection = seatNumber - 100;
        }

        return section + "-" + seatInSection;
    }

    /**
     * 특정 콘서트의 캐시 상태 확인
     */
    public Map<String, Object> getCacheStatus(Long concertId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        Map<String, Object> status = Map.of(
                "concertId", concertId,
                "cacheKey", key,
                "totalSeats", seatMap.size(),
                "availableSeats", seatMap.values().stream()
                        .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.AVAILABLE ? 1 : 0)
                        .sum(),
                "reservedSeats", seatMap.values().stream()
                        .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.RESERVED ? 1 : 0)
                        .sum(),
                "bookedSeats", seatMap.values().stream()
                        .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.BOOKED ? 1 : 0)
                        .sum()
        );

        log.info("캐시 상태 조회: {}", status);
        return status;
    }

    /**
     * 특정 콘서트의 캐시 삭제 (개선된 버전)
     * - 캐시 존재 여부 확인
     * - 적절한 예외 처리
     * - 명확한 응답 메시지
     */
    public String clearSeatCache(Long concertId) {
        try {
            String key = SEAT_STATUS_KEY_PREFIX + concertId;
            RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

            // 1. 캐시 존재 여부 확인
            if (!seatMap.isExists()) {
                log.info("삭제할 좌석 캐시가 존재하지 않음: concertId={}, key={}", concertId, key);
                return "삭제할 캐시가 없습니다.";
            }

            // 2. 캐시 크기 확인 (삭제 전 로깅용)
            int cacheSize = seatMap.size();

            // 3. 캐시 삭제 실행
            seatMap.clear();

            // 4. 삭제 완료 확인
            boolean isCleared = !seatMap.isExists() || seatMap.size() == 0;

            if (isCleared) {
                log.info("좌석 캐시 삭제 완료: concertId={}, deletedItems={}, key={}",
                        concertId, cacheSize, key);
                return "좌석 캐시 삭제 성공";
            } else {
                log.warn("좌석 캐시 삭제 실패 (일부 데이터 남음): concertId={}, remainingItems={}",
                        concertId, seatMap.size());
                return "캐시 삭제 중 일부 오류가 발생했습니다.";
            }

        } catch (Exception e) {
            log.error("좌석 캐시 삭제 중 예외 발생: concertId={}, error={}", concertId, e.getMessage(), e);
            throw new RuntimeException("캐시 삭제 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
}