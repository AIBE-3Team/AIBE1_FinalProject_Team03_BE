package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

/*
 * Seller Concert Create DTO
 * 판매자용 콘서트 생성 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerConcertCreateDTO {

	@NotBlank(message = "콘서트 제목은 필수입니다")
	@Size(max = 100, message = "콘서트 제목은 100자 이하여야 합니다")
	private String title;

	@NotBlank(message = "아티스트명은 필수입니다")
	@Size(max = 50, message = "아티스트명은 50자 이하여야 합니다")
	private String artist;

	@Size(max = 1000, message = "콘서트 설명은 1000자 이하여야 합니다")
	private String description;

	@NotBlank(message = "공연장명은 필수입니다")
	@Size(max = 100, message = "공연장명은 100자 이하여야 합니다")
	private String venueName;

	@Size(max = 200, message = "공연장 주소는 200자 이하여야 합니다")
	private String venueAddress;

	@NotNull(message = "콘서트 날짜는 필수입니다")
	@Future(message = "콘서트 날짜는 현재 날짜보다 이후여야 합니다")
	private LocalDate concertDate;

	@NotNull(message = "시작 시간은 필수입니다")
	private LocalTime startTime;

	@NotNull(message = "종료 시간은 필수입니다")
	private LocalTime endTime;

	@NotNull(message = "총 좌석 수는 필수입니다")
	@Positive(message = "총 좌석 수는 양수여야 합니다")
	@Max(value = 100000, message = "총 좌석 수는 100,000석 이하여야 합니다")
	private Integer totalSeats;

	@NotNull(message = "예매 시작일시는 필수입니다")
	@Future(message = "예매 시작일시는 현재 시간보다 이후여야 합니다")
	private LocalDateTime bookingStartDate;

	@NotNull(message = "예매 종료일시는 필수입니다")
	@Future(message = "예매 종료일시는 현재 시간보다 이후여야 합니다")
	private LocalDateTime bookingEndDate;

	@Min(value = 0, message = "최소 연령은 0세 이상이어야 합니다")
	@Max(value = 100, message = "최소 연령은 100세 이하여야 합니다")
	private Integer minAge;

	@Min(value = 1, message = "사용자당 최대 티켓 수는 1개 이상이어야 합니다")
	@Max(value = 10, message = "사용자당 최대 티켓 수는 10개 이하여야 합니다")
	private Integer maxTicketsPerUser;

	@Pattern(regexp = "^https?://.*\\.(jpg|jpeg|png|gif|webp)$",
		message = "포스터 이미지 URL은 올바른 이미지 URL 형식이어야 합니다")
	private String posterImageUrl;
}