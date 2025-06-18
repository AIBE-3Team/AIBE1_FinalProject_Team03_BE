package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
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

	@Future(message = "시작 날짜는 현재 날짜 이후여야 합니다")
	private LocalDate startDate;

	@Future(message = "종료 날짜는 현재 날짜 이후여야 합니다")
	private LocalDate endDate;

	@DecimalMin(value = "0", message = "최소 가격은 0원 이상이어야 합니다")
	@Digits(integer = 10, fraction = 2, message = "가격은 올바른 형식이어야 합니다")
	private BigDecimal priceMin;

	@DecimalMin(value = "0", message = "최대 가격은 0원 이상이어야 합니다")
	@Digits(integer = 10, fraction = 2, message = "가격은 올바른 형식이어야 합니다")
	private BigDecimal priceMax;

	/**
	 * 날짜 범위 순서 검증
	 * 시작 날짜가 종료 날짜보다 늦으면 안됨
	 */
	@AssertTrue(message = "종료 날짜는 시작 날짜와 같거나 늦어야 합니다")
	public boolean isValidDateRange() {
		// null 값이 있으면 검증 통과 (옵셔널 필터이므로)
		if (startDate == null || endDate == null) {
			return true;
		}
		// 시작 날짜가 종료 날짜보다 늦으면 false
		return !startDate.isAfter(endDate);
	}

	/**
	 * 가격 범위 순서 검증
	 * 최소 가격이 최대 가격보다 크면 안됨
	 */
	@AssertTrue(message = "최대 가격은 최소 가격보다 크거나 같아야 합니다")
	public boolean isValidPriceRange() {
		// null 값이 있으면 검증 통과 (옵셔널 필터이므로)
		if (priceMin == null || priceMax == null) {
			return true;
		}
		// 최소 가격이 최대 가격보다 크면 false
		return priceMin.compareTo(priceMax) <= 0;
	}

	/**
	 * 날짜 범위 유효성 검증 (너무 긴 범위 방지)
	 * 최대 1년까지만 허용
	 */
	@AssertTrue(message = "날짜 범위는 1년을 초과할 수 없습니다")
	public boolean isValidDateRangeLength() {
		if (startDate == null || endDate == null) {
			return true;
		}
		// 1년(365일) 초과하는 범위는 불허
		return startDate.plusYears(1).isAfter(endDate) || startDate.plusYears(1).equals(endDate);
	}

	/**
	 * 가격 범위 유효성 검증 (너무 큰 범위 방지)
	 * 최대 가격이 최소 가격의 1000배를 초과하면 안됨
	 */
	@AssertTrue(message = "가격 범위가 너무 큽니다")
	public boolean isValidPriceRangeSize() {
		if (priceMin == null || priceMax == null) {
			return true;
		}
		// 최소 가격이 0이면 별도 처리
		if (priceMin.compareTo(BigDecimal.ZERO) == 0) {
			return priceMax.compareTo(new BigDecimal("10000000")) <= 0; // 1천만원 한도
		}
		// 최대 가격이 최소 가격의 1000배 이하여야 함
		BigDecimal maxAllowed = priceMin.multiply(new BigDecimal("1000"));
		return priceMax.compareTo(maxAllowed) <= 0;
	}
}