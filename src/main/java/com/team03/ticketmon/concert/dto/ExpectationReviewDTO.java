package com.team03.ticketmon.concert.dto;

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
	private Long userId;
	private String userNickname;
	private String comment;
	// 1-5 기대 점수
	private Integer expectationRating;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
