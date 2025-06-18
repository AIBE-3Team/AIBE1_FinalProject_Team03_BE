package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 좌석 상태 캐시 초기화 서비스
 * - 콘서트 예매 시작 시 DB 데이터를 Redis로 Warm-up
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatCacheInitService {

    private final RedissonClient redissonClient;
    private static final String SEAT_STATUS_KEY_PREFIX = "seat:status:";

    /**
     * 특정 콘서트의 좌석 캐시 초기화
     * - 실제 운영에서는 DB에서 좌석 정보를 가져와서 Redis에 적재
     * - 현재는 테스트용 더미 데이터로 초기화
     */
    public void initializeSeatCache(Long concertId, int totalSeats) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        // 기존 캐시 클리어
        seatMap.clear();

        // 더미 좌석 데이터 생성 및 캐시에 저장
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

            seatMap.put(String.valueOf(i), seatStatus);
        }

        log.info("좌석 캐시 초기화 완료: concertId={}, totalSeats={}", concertId, totalSeats);
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
        // 3. Redis Hash에 좌석 상태 저장

        // 예시 구조:
        /*
        List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertId(concertId);
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);
        
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
            
            seatMap.put(concertSeat.getSeat().getId().toString(), seatStatus);
        }
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
     * 특정 콘서트의 캐시 삭제
     */
    public void clearSeatCache(Long concertId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);
        seatMap.clear();

        log.info("좌석 캐시 삭제 완료: concertId={}", concertId);
    }
}