package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

import com.team03.ticketmon._global.entity.BaseTimeEntity;

/**
 * Review Entity
 * 실제 관람 후기 관리
 */

@Entity
@Getter
@Setter
@Table(name = "reviews")
@ToString(exclude = {"concert"})
@EqualsAndHashCode(of = "id", callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class Review extends BaseTimeEntity {
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

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	// 1-5 실제 관람 후 평점
	@Min(value = 1, message = "평점은 1 이상이어야 합니다")
	@Max(value = 5, message = "평점은 5 이하여야 합니다")
	@Column(name = "rating", nullable = false)
	private Integer rating;
}
