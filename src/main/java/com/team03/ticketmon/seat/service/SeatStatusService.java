package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis Hash를 활용한 좌석 상태 관리 서비스
 * 키 구조: seat:status:{concertId} -> Hash(seatId -> SeatStatus)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStatusService {

    private final RedissonClient redissonClient;

    // Redis 키 패턴
    private static final String SEAT_STATUS_KEY_PREFIX = "seat:status:";

    /**
     * 특정 콘서트의 전체 좌석 상태 조회
     */
    public Map<Long, SeatStatus> getAllSeatStatus(Long concertId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        // String 키를 Long으로 변환하여 반환
        return seatMap.readAllMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Long.valueOf(entry.getKey()),
                        Map.Entry::getValue
                ));
    }

    /**
     * 특정 좌석 상태 조회
     */
    public Optional<SeatStatus> getSeatStatus(Long concertId, Long seatId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        SeatStatus status = seatMap.get(seatId.toString());
        return Optional.ofNullable(status);
    }

    /**
     * 좌석 상태 업데이트
     */
    public void updateSeatStatus(SeatStatus seatStatus) {
        String key = SEAT_STATUS_KEY_PREFIX + seatStatus.getConcertId();
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        seatMap.put(seatStatus.getSeatId().toString(), seatStatus);
        log.info("좌석 상태 업데이트: concertId={}, seatId={}, status={}",
                seatStatus.getConcertId(), seatStatus.getSeatId(), seatStatus.getStatus());
    }

    /**
     * 좌석 임시 선점 (5분 TTL)
     */
    public SeatStatus reserveSeat(Long concertId, Long seatId, Long userId, String seatInfo) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(5); // 5분 후 만료

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

        updateSeatStatus(reservedStatus);
        log.info("좌석 선점 완료: concertId={}, seatId={}, userId={}, expiresAt={}",
                concertId, seatId, userId, expiresAt);

        return reservedStatus;
    }

    /**
     * 좌석 선점 해제 (AVAILABLE로 변경)
     */
    public void releaseSeat(Long concertId, Long seatId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, seatId);

        if (currentStatus.isPresent()) {
            SeatStatus updatedStatus = SeatStatus.builder()
                    .id(concertId + "-" + seatId)
                    .concertId(concertId)
                    .seatId(seatId)
                    .status(SeatStatusEnum.AVAILABLE)
                    .userId(null)
                    .reservedAt(null)
                    .expiresAt(null)
                    .seatInfo(currentStatus.get().getSeatInfo())
                    .build();

            updateSeatStatus(updatedStatus);
            log.info("좌석 선점 해제: concertId={}, seatId={}", concertId, seatId);
        }
    }

    /**
     * 좌석 예매 완료 처리
     */
    public void bookSeat(Long concertId, Long seatId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, seatId);

        if (currentStatus.isPresent()) {
            SeatStatus bookedStatus = SeatStatus.builder()
                    .id(concertId + "-" + seatId)
                    .concertId(concertId)
                    .seatId(seatId)
                    .status(SeatStatusEnum.BOOKED)
                    .userId(currentStatus.get().getUserId())
                    .reservedAt(currentStatus.get().getReservedAt())
                    .expiresAt(null) // 예매 완료 시 만료 시간 제거
                    .seatInfo(currentStatus.get().getSeatInfo())
                    .build();

            updateSeatStatus(bookedStatus);
            log.info("좌석 예매 완료: concertId={}, seatId={}", concertId, seatId);
        }
    }

    /**
     * 만료된 선점 좌석들 정리
     */
    public void cleanupExpiredReservations(Long concertId) {
        Map<Long, SeatStatus> allSeats = getAllSeatStatus(concertId);
        LocalDateTime now = LocalDateTime.now();

        for (SeatStatus seat : allSeats.values()) {
            if (seat.isReserved() && seat.getExpiresAt() != null && now.isAfter(seat.getExpiresAt())) {
                releaseSeat(concertId, seat.getSeatId());
                log.info("만료된 선점 좌석 해제: concertId={}, seatId={}", concertId, seat.getSeatId());
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