package com.team03.ticketmon.payment.dto;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.venue.domain.Seat;
import lombok.Getter;

import java.time.format.DateTimeFormatter;
import java.util.List;

//프론트 결제 결과페이지 전송 데이터
@Getter
public class PaymentResponseDto {
    private final String bookingNumber;
    private final String concertTitle;
    private final double totalAmount;
    private final String concertDateTime;
    private final List<String> seatLabels;

    public PaymentResponseDto(Booking booking) {
        this.bookingNumber = booking.getBookingNumber();
        this.concertTitle = booking.getConcert().getTitle();
        this.totalAmount = booking.getTotalAmount().doubleValue();

        // 공연 일시: yyyy-MM-dd HH:mm 포맷
        Concert c = booking.getConcert();
        this.concertDateTime = c.getConcertDate().format(DateTimeFormatter.ISO_DATE)
                + " "
                + c.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"));

        // 좌석 라벨: 섹션+열+번호 (예: A-3-12)
        this.seatLabels = booking.getTickets().stream()
                .map(t -> {
                    Seat s = t.getConcertSeat().getSeat();
                    return s.getSection()
                            + "-" + s.getSeatRow()
                            + "-" + s.getSeatNumber();
                })
                .toList();
    }
}
