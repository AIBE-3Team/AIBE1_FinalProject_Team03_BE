package com.team03.ticketmon.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UserBookingSummaryDTO(
        String bookingNumber,
        String concertTitle,
        LocalDate concertDate,
        String bookingStatus,
        BigDecimal totalAmount,
        String posterImageUrl
) {
}
