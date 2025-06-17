package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Expectation Review Entity
 * 관람 전 기대평 관리
 */

@Entity
@Table(name = "expectation_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpectationReview {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_id", nullable = false)
	private Concert concert;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "user_nickname", nullable = false)
	private String userNickname;

	@Column(columnDefinition = "TEXT")
	private String comment;

	// 1-5 기대 점수
	@Column(name = "expectation_rating")
	private Integer expectationRating;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt = LocalDateTime.now();
}
