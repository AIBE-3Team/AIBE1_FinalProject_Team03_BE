package com.team03.ticketmon.seat.service;

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
import org.springframework.transaction.annotation.Transactional;

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
    private final ConcertSeatRepository concertSeatRepository;
    private static final String SEAT_STATUS_KEY_PREFIX = "seat:status:";

    /**
     * 특정 콘서트의 좌석 캐시 초기화 (성능 최적화 버전) - 기존 메서드
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
     * ✨ DB 기반 좌석 캐시 초기화 메서드
     * 실제 DB 데이터를 기반으로 좌석 상태 캐시를 초기화합니다.
     *
     * @param concertId 콘서트 ID
     */
    @Transactional(readOnly = true)
    public void initializeSeatCacheFromDB(Long concertId) {
        log.info("DB 기반 좌석 캐시 초기화 시작: concertId={}", concertId);

        try {
            // 1. DB에서 콘서트의 모든 좌석 정보 조회 (Fetch Join 적용)
            List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            if (concertSeats.isEmpty()) {
                log.warn("콘서트 좌석 데이터가 없습니다: concertId={}", concertId);
                return;
            }

            // 2. Redis 캐시 구조 준비
            String key = SEAT_STATUS_KEY_PREFIX + concertId;
            RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

            // 기존 캐시 클리어
            seatMap.clear();

            // 3. 로컬 맵에 모든 좌석 상태 준비 (배치 처리 최적화)
            Map<String, SeatStatus> batchSeatData = new HashMap<>();

            for (ConcertSeat concertSeat : concertSeats) {
                // 4. 좌석 정보 추출
                Seat seat = concertSeat.getSeat();
                Long seatId = seat.getSeatId();

                // 5. 예매 여부 확인 (Ticket 존재 여부로 판별)
                boolean isBooked = concertSeat.getTicket() != null;
                SeatStatusEnum status = isBooked ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE;

                // 6. 좌석 정보 문자열 생성 (실제 DB 데이터 기반)
                String seatInfo = generateSeatInfoFromDB(seat);

                // 7. SeatStatus 객체 생성
                SeatStatus seatStatus = SeatStatus.builder()
                        .id(concertId + "-" + seatId)
                        .concertId(concertId)
                        .seatId(seatId)
                        .status(status)
                        .userId(null) // 초기화 시에는 선점 사용자 없음
                        .reservedAt(null)
                        .expiresAt(null)
                        .seatInfo(seatInfo)
                        .build();

                // 8. 로컬 맵에 누적 (Redis 호출 없음)
                batchSeatData.put(seatId.toString(), seatStatus);
            }

            // 9. 한 번의 Redis 호출로 모든 데이터 일괄 저장
            seatMap.putAll(batchSeatData);

            log.info("DB 기반 좌석 캐시 초기화 완료 (배치 처리): concertId={}, totalSeats={}, bookedSeats={}",
                    concertId,
                    batchSeatData.size(),
                    batchSeatData.values().stream()
                            .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.BOOKED ? 1 : 0)
                            .sum());

        } catch (Exception e) {
            log.error("DB 기반 좌석 캐시 초기화 중 오류 발생: concertId={}", concertId, e);
            throw new RuntimeException("좌석 캐시 초기화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * ✨ 실제 DB 데이터 기반 좌석 정보 생성
     * Seat 엔티티의 section과 seatNumber를 조합하여 좌석 정보 문자열을 생성합니다.
     *
     * @param seat Seat 엔티티
     * @return 좌석 정보 문자열 (예: A-15, B-23, VIP-5)
     */
    private String generateSeatInfoFromDB(Seat seat) {
        if (seat == null) {
            log.warn("Seat 정보가 null입니다.");
            return "UNKNOWN";
        }

        String section = seat.getSection() != null ? seat.getSection() : "?";
        String seatRow = seat.getSeatRow() != null ? seat.getSeatRow() : "?";
        int seatNumber = seat.getSeatNumber() != null ? seat.getSeatNumber() : 0;

        return section + "-" + seatRow + "-" + seatNumber;
    }

    /**
     * 좌석 정보 문자열 생성 (더미 데이터용) - 기존 메서드
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
     * 특정 콘서트의 캐시 상태 확인 - 기존 메서드
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
     * 특정 콘서트의 캐시 삭제 (개선된 버전) - 기존 메서드
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