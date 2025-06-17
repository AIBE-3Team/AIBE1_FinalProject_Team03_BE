package com.team03.ticketmon.concert.dto;

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
	private Long userId;
	private String userNickname;
	private String title;
	private String description;
	// 1-5 실제 관람 후 평점
	private Integer rating;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
