package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/*
 * Review DTO
 * 후기 정보 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {
	private Long id;

	private Long concertId;

	@NotNull(message = "사용자 ID는 필수입니다")
	private Long userId;

	@NotBlank(message = "사용자 닉네임은 필수입니다")
	@Size(max = 50, message = "사용자 닉네임은 50자 이하여야 합니다")
	private String userNickname;

	@NotBlank(message = "후기 제목은 필수입니다")
	@Size(max = 100, message = "후기 제목은 100자 이하여야 합니다")
	private String title;

	@NotBlank(message = "후기 내용은 필수입니다")
	@Size(max = 1000, message = "후기 내용은 1000자 이하여야 합니다")
	private String description;

	// 1-5 실제 관람 후 평점
	@NotNull(message = "평점은 필수입니다")
	@Min(value = 1, message = "평점은 1 이상이어야 합니다")
	@Max(value = 5, message = "평점은 5 이하여야 합니다")
	private Integer rating;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}