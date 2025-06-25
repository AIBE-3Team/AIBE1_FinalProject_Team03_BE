package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.booking.repository.TicketRepository;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import com.team03.ticketmon.venue.domain.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final TicketRepository ticketRepository;
    private final ConcertSeatRepository concertSeatRepository;

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
     * 실제 DB 데이터를 기반으로 특정 콘서트의 좌석 캐시를 초기화(Warm-up)합니다.
     * Redis 성능을 위해 모든 좌석 상태를 Map으로 만든 후 한 번에 저장합니다.
     *
     * @param concertId 캐시를 초기화할 콘서트의 ID
     */
    public void initializeSeatCacheFromDB(Long concertId) {
        log.info("DB 기반 좌석 캐시 초기화 시작: concertId={}", concertId);

        // 1. DB에서 해당 콘서트의 모든 좌석 정보를 조회 (N+1 방지)
        List<ConcertSeat> concertSeats = concertSeatRepository.findAllByConcertIdWithSeat(concertId);
        if (concertSeats.isEmpty()) {
            log.warn("캐시를 초기화할 좌석 정보가 DB에 존재하지 않습니다. concertId: {}", concertId);
            return;
        }

        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        // 2. 로컬 Map에 모든 좌석 상태를 미리 준비 (Redis 네트워크 호출 최소화)
        Map<String, SeatStatus> batchSeatData = new HashMap<>();
        for (ConcertSeat concertSeat : concertSeats) {
            // 2-1. 티켓 존재 여부를 확인하여 좌석의 현재 상태 결정
            boolean isBooked = ticketRepository.existsByConcertSeat_ConcertSeatId(concertSeat.getConcertSeatId());
            SeatStatusEnum currentStatus = isBooked ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE;

            Seat physicalSeat = concertSeat.getSeat();
            String seatInfo = String.format("%s-%s-%d",
                    physicalSeat.getSection(),
                    physicalSeat.getSeatRow(),
                    physicalSeat.getSeatNumber());

            SeatStatus seatStatus = SeatStatus.builder()
                    .id(concertId + "-" + physicalSeat.getSeatId())
                    .concertId(concertId)
                    .seatId(physicalSeat.getSeatId())
                    .status(currentStatus)
                    .userId(null) // 초기화 시점에는 선점자가 없음
                    .seatInfo(seatInfo)
                    .build();
            // 2-2. 로컬 맵에 누적
            batchSeatData.put(String.valueOf(physicalSeat.getSeatId()), seatStatus);
        }

        // 3. 기존 캐시를 모두 지우고, 한 번의 Redis 호출로 모든 데이터 일괄 저장 (원자성 보장)
        seatMap.clear();
        seatMap.putAll(batchSeatData);

        log.info("DB 기반 좌석 캐시 초기화 완료 (배치 처리): concertId={}, 총 좌석 수={}",
                concertId, batchSeatData.size());
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