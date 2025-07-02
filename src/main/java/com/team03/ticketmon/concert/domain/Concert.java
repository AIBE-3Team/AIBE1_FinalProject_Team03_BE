package com.team03.ticketmon.concert.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.team03.ticketmon._global.entity.BaseTimeEntity;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Concert Entity
 * 콘서트 정보를 관리하는 엔티티
 */

@Builder
@Entity
@Table(name = "concerts")
@Getter
@Setter
@ToString(exclude = {"concertSeats", "bookings", "reviews"})
@EqualsAndHashCode(of = "concertId", callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class Concert extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "concert_id")
	private Long concertId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String artist;

	@Column(nullable = false)
	private Long sellerId;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "venue_name", nullable = false)
	private String venueName;

	@Column(name = "venue_address", columnDefinition = "TEXT")
	private String venueAddress;

	@Column(name = "concert_date", nullable = false)
	private LocalDate concertDate;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "total_seats", nullable = false)
	private Integer totalSeats;

	@Column(name = "booking_start_date", nullable = false)
	private LocalDateTime bookingStartDate;

	@Column(name = "booking_end_date", nullable = false)
	private LocalDateTime bookingEndDate;

	@Column(name = "min_age", nullable = false)
	@Builder.Default
	private Integer minAge = 0;

	@Column(name = "max_tickets_per_user", nullable = false)
	@Builder.Default
	private Integer maxTicketsPerUser = 4;

	// ENUM을 문자열로 저장 (ORDINAL 사용 금지)
	@Enumerated(EnumType.STRING)
	@Column(length = 20, nullable = false)
	@Builder.Default
	private ConcertStatus status = ConcertStatus.SCHEDULED;

	@Column(name = "poster_image_url", columnDefinition = "TEXT")
	private String posterImageUrl;

	@Column(name = "ai_summary", columnDefinition = "TEXT")
	private String aiSummary;

	@Column(name = "ai_summary_retry_count")
	@Builder.Default
	private Integer aiSummaryRetryCount = 0;

	@Column(name = "ai_summary_last_failed_at")
	private LocalDateTime aiSummaryLastFailedAt;

	// 리뷰 변동성 추적을 위한 필드들
	@Column(name = "ai_summary_generated_at")
	private LocalDateTime aiSummaryGeneratedAt;

	@Column(name = "ai_summary_review_count")
	@Builder.Default
	private Integer aiSummaryReviewCount = 0; // 요약 생성 시 사용된 리뷰 개수

	@Column(name = "ai_summary_review_checksum")
	private String aiSummaryReviewChecksum; // 리뷰 내용 변경 감지용

	@Column(name = "last_review_modified_at")
	private LocalDateTime lastReviewModifiedAt; // 마지막 리뷰 변경 시점

	@OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<ConcertSeat> concertSeats;

	@OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Booking> bookings;

	@OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Review> reviews;

	public boolean isQueueActive() {
		// 대기열은 ON_SALE 상태일 때만 활성화된다고 정책을 일단 정의
		return this.status == ConcertStatus.ON_SALE;
	}
}
