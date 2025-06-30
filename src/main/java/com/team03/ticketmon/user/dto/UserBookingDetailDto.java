package com.team03.ticketmon.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record UserBookingDetailDto(
        String bookingNumber,
        String concertTitle,
        String artistName,
        LocalDate concertDate,
        LocalTime startTime,
        LocalTime endTime,
        String venueName,
        String venueAddress,
        BigDecimal totalAmount,
        String bookingStatus,     // ex: CONFIRMED, CANCELED
        String paymentStatus,     // ex: DONE, PENDING
        String paymentMethod,     // ex: CARD, KAKAOPAY
        List<String> seatList,    // ex: ["R석 A열 3번", "R석 A열 4번"]
        String posterImageUrl,
        LocalDateTime bookedAt
) {
}
