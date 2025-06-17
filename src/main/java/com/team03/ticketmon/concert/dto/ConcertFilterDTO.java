package com.team03.ticketmon.concert.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Concert Filter DTO
 * 콘서트 필터 조건 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConcertFilterDTO {
	private LocalDate startDate;
	private LocalDate endDate;
	private BigDecimal priceMin;
	private BigDecimal priceMax;
}
