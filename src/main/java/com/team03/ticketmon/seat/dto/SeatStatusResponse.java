package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;

import java.time.LocalDateTime;

/**
 * 좌석 상태 응답 DTO
 */
public record SeatStatusResponse(
        Long concertId,
        Long seatId,
        String seatInfo,
        SeatStatusEnum status,
        Long userId,
        LocalDateTime reservedAt,
        LocalDateTime expiresAt,
        Long remainingSeconds
) {

    /**
     * SeatStatus 엔티티로부터 응답 DTO 생성
     */
    public static SeatStatusResponse from(SeatStatus seatStatus) {
        Long remainingSeconds = null;
        if (seatStatus.isReserved() && seatStatus.getExpiresAt() != null) {
            remainingSeconds = java.time.Duration.between(
                    LocalDateTime.now(),
                    seatStatus.getExpiresAt()
            ).getSeconds();
            remainingSeconds = Math.max(0, remainingSeconds); // 음수 방지
        }

        return new SeatStatusResponse(
                seatStatus.getConcertId(),
                seatStatus.getSeatId(),
                seatStatus.getSeatInfo(),
                seatStatus.getStatus(),
                seatStatus.getUserId(),
                seatStatus.getReservedAt(),
                seatStatus.getExpiresAt(),
                remainingSeconds
        );
    }
}