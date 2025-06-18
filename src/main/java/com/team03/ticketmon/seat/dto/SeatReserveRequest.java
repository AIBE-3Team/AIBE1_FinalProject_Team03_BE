package com.team03.ticketmon.seat.dto;

/**
 * 좌석 선점 요청 DTO
 */
public record SeatReserveRequest(
        Long concertId,
        Long seatId,
        Long userId
) {
    // Record는 자동으로 validation을 위한 메서드를 제공하지 않으므로
    // 필요시 별도 validation 로직 추가
}