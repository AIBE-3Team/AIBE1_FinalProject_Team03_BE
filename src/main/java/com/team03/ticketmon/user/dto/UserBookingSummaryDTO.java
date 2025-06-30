package com.team03.ticketmon.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UserBookingSummaryDTO(
        String bookingNumber,
        String concertTitle,
        LocalDate concertDate,
        String venueName,
        String venueAddress,
        String bookingStatus,
        BigDecimal totalAmount,
        String posterImageUrl,
        List<String> seatList
) {
}
