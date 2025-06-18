package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

/*
 * Seller Concert Update DTO
 * 판매자용 콘서트 수정 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerConcertUpdateDTO {

	@Size(max = 100, message = "콘서트 제목은 100자 이하여야 합니다")
	private String title;

	@Size(max = 50, message = "아티스트명은 50자 이하여야 합니다")
	private String artist;

	@Size(max = 1000, message = "콘서트 설명은 1000자 이하여야 합니다")
	private String description;

	@Size(max = 100, message = "공연장명은 100자 이하여야 합니다")
	private String venueName;

	@Size(max = 200, message = "공연장 주소는 200자 이하여야 합니다")
	private String venueAddress;

	@Future(message = "콘서트 날짜는 현재 날짜보다 이후여야 합니다")
	private LocalDate concertDate;

	private LocalTime startTime;
	private LocalTime endTime;

	@Positive(message = "총 좌석 수는 양수여야 합니다")
	@Max(value = 100000, message = "총 좌석 수는 100,000석 이하여야 합니다")
	private Integer totalSeats;

	@Future(message = "예매 시작일시는 현재 시간보다 이후여야 합니다")
	private LocalDateTime bookingStartDate;

	@Future(message = "예매 종료일시는 현재 시간보다 이후여야 합니다")
	private LocalDateTime bookingEndDate;

	@Min(value = 0, message = "최소 연령은 0세 이상이어야 합니다")
	@Max(value = 100, message = "최소 연령은 100세 이하여야 합니다")
	private Integer minAge;

	@Min(value = 1, message = "사용자당 최대 티켓 수는 1개 이상이어야 합니다")
	@Max(value = 10, message = "사용자당 최대 티켓 수는 10개 이하여야 합니다")
	private Integer maxTicketsPerUser;

	private ConcertStatus status;

	@Pattern(regexp = "^https?://.*\\.(jpg|jpeg|png|gif|webp)$",
		message = "포스터 이미지 URL은 올바른 이미지 URL 형식이어야 합니다")
	private String posterImageUrl;

	/**
	 * Update DTO 전용: 최소 하나의 필드는 수정되어야 함
	 */
	@AssertTrue(message = "수정할 항목이 최소 하나는 있어야 합니다")
	public boolean hasAtLeastOneField() {
		return title != null || artist != null || description != null ||
			venueName != null || venueAddress != null || concertDate != null ||
			startTime != null || endTime != null || totalSeats != null ||
			bookingStartDate != null || bookingEndDate != null ||
			minAge != null || maxTicketsPerUser != null || status != null ||
			posterImageUrl != null;
	}

	/**
	 * 상태 변경 유효성 검증
	 */
	@AssertTrue(message = "유효하지 않은 상태 변경입니다")
	public boolean isValidStatusChange() {
		if (status == null) {
			return true; // 상태 변경하지 않는 경우
		}

		// 비즈니스 규칙: CANCELLED 상태로만 변경 가능하다고 가정
		// 실제 비즈니스 규칙에 따라 수정 필요
		return status == ConcertStatus.CANCELLED ||
			status == ConcertStatus.SCHEDULED ||
			status == ConcertStatus.ON_SALE;
	}

	/**
	 * 공연 시간 순서 검증 (nullable 고려)
	 */
	@AssertTrue(message = "종료 시간은 시작 시간보다 늦어야 합니다")
	public boolean isValidPerformanceTimes() {
		if (startTime == null || endTime == null) {
			return true; // null이면 검증 패스 (부분 업데이트 허용)
		}
		return endTime.isAfter(startTime);
	}

	/**
	 * 예매 시간 순서 검증 (nullable 고려)
	 */
	@AssertTrue(message = "예매 종료일시는 예매 시작일시보다 늦어야 합니다")
	public boolean isValidBookingTimes() {
		if (bookingStartDate == null || bookingEndDate == null) {
			return true; // null이면 검증 패스 (부분 업데이트 허용)
		}
		return bookingEndDate.isAfter(bookingStartDate);
	}

	/**
	 * 예매 기간과 공연 날짜 검증 (nullable 고려)
	 */
	@AssertTrue(message = "예매 종료일시는 공연 시작 전이어야 합니다")
	public boolean isValidBookingPeriod() {
		if (bookingEndDate == null || concertDate == null || startTime == null) {
			return true; // 필요한 값이 없으면 검증 패스
		}
		LocalDateTime concertStartDateTime = concertDate.atTime(startTime);
		return bookingEndDate.isBefore(concertStartDateTime);
	}
}