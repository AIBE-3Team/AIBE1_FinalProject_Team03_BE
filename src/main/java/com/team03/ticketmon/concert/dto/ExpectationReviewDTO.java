package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/*
 * Expectation Review DTO
 * 기대평 정보 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpectationReviewDTO {
	private Long id;
	private Long concertId;

	@NotNull(message = "사용자 ID는 필수입니다")
	private Long userId;

	@NotBlank(message = "사용자 닉네임은 필수입니다")
	@Size(max = 50, message = "사용자 닉네임은 50자 이하여야 합니다")
	private String userNickname;

	@NotBlank(message = "기대평 내용은 필수입니다")
	@Size(max = 500, message = "기대평 내용은 500자 이하여야 합니다")
	private String comment;

	// 1-5 기대 점수
	@NotNull(message = "기대 점수는 필수입니다")
	@Min(value = 1, message = "기대 점수는 1 이상이어야 합니다")
	@Max(value = 5, message = "기대 점수는 5 이하여야 합니다")
	private Integer expectationRating;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}