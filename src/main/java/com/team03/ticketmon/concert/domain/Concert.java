package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

import com.team03.ticketmon.concert.domain.enums.ConcertStatus;

/**
 * Concert Entity
 * 콘서트 정보를 관리하는 엔티티
 */

@Entity
@Table(name = "concerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Concert {
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
	private Integer minAge = 0;

	@Column(name = "max_tickets_per_user", nullable = false)
	private Integer maxTicketsPerUser = 4;

	// ENUM을 문자열로 저장 (ORDINAL 사용 금지)
	@Enumerated(EnumType.STRING)
	@Column(length = 20, nullable = false)
	private ConcertStatus status = ConcertStatus.SCHEDULED;

	@Column(name = "poster_image_url", columnDefinition = "TEXT")
	private String posterImageUrl;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt = LocalDateTime.now();

	@Column(name = "ai_summary", columnDefinition = "TEXT")
	private String aiSummary;

	@OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<ConcertSeat> concertSeats;

	@OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Booking> bookings;

	@OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Review> reviews;
}