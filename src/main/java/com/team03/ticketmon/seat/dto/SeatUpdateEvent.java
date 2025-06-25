package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 좌석 상태 변경 이벤트 DTO
 * - Redis Pub/Sub으로 전송되는 메시지 구조
 * - JSON 직렬화/역직렬화를 위한 record 타입 사용
 */
@Builder
public record SeatUpdateEvent(
        Long concertId,        // 콘서트 ID
        Long seatId,           // 좌석 ID  
        SeatStatusEnum status, // 변경된 좌석 상태 (AVAILABLE, RESERVED, BOOKED)
        Long userId,           // 사용자 ID (AVAILABLE일 때는 null)
        String seatInfo,       // 좌석 정보 (A-1, B-25 등)
        LocalDateTime timestamp // 이벤트 발생 시간
) {

    /**
     * SeatStatus 도메인 객체로부터 이벤트 생성
     *
     * @param seatStatus 좌석 상태 도메인 객체
     * @return SeatUpdateEvent 객체
     */
    public static SeatUpdateEvent from(com.team03.ticketmon.seat.domain.SeatStatus seatStatus) {
        return SeatUpdateEvent.builder()
                .concertId(seatStatus.getConcertId())
                .seatId(seatStatus.getSeatId())
                .status(seatStatus.getStatus())
                .userId(seatStatus.getUserId())
                .seatInfo(seatStatus.getSeatInfo())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 개별 필드로부터 이벤트 생성
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param status 좌석 상태
     * @param userId 사용자 ID
     * @param seatInfo 좌석 정보
     * @return SeatUpdateEvent 객체
     */
    public static SeatUpdateEvent of(Long concertId, Long seatId, SeatStatusEnum status,
                                     Long userId, String seatInfo) {
        return SeatUpdateEvent.builder()
                .concertId(concertId)
                .seatId(seatId)
                .status(status)
                .userId(userId)
                .seatInfo(seatInfo)
                .timestamp(LocalDateTime.now())
                .build();
    }
}