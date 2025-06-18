package com.team03.ticketmon.seat.exception;

/**
 * 좌석 예약 관련 비즈니스 예외
 * - 좌석 선점 실패, 중복 예약, 가용성 문제 등을 처리
 */
public class SeatReservationException extends RuntimeException {

    /**
     * 메시지만으로 예외 생성
     */
    public SeatReservationException(String message) {
        super(message);
    }

    /**
     * 메시지와 원인 예외로 예외 생성
     */
    public SeatReservationException(String message, Throwable cause) {
        super(message, cause);
    }
}