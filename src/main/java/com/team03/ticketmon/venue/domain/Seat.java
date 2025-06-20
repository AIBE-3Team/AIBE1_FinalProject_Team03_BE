package com.team03.ticketmon.venue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공연장의 '물리적인 개별 좌석' 정보를 나타내는 엔티티 (Physical Seat Entity)
 * 이 엔티티는 좌석의 위치(구역, 열, 번호)와 같은 고정된 정보를 정의
 *
 * 특정 콘서트에서의 가격, 등급, 예매 가능 상태 등 가변적인 정보는
 * 'ConcertSeat' 엔티티와 Redis의 'SeatStatus'에서 별도로 관리
 */
@Entity
@Table(
		name = "seats",
		uniqueConstraints = {
				// 한 공연장 내에서 구역, 열, 번호의 조합은 유일
				@UniqueConstraint(columnNames = {"venue_id", "section", "seat_row", "seat_number"})
		}
)
@Getter
@NoArgsConstructor
public class Seat {

	/** 물리 좌석의 고유 ID (Primary Key) */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "seat_id")
	private Long seatId;

	/** 이 좌석이 속한 공연장 (Foreign Key) */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "venue_id", nullable = false)
	private Venue venue;

	/** 좌석 구역 (예: A, B, 스탠딩, 1층, 2층) */
	@Column(nullable = false, length = 50)
	private String section;

	/** 좌석 열 (예: 1, 2, R) */
	@Column(name = "seat_row", nullable = false, length = 20)
	private String seatRow;

	/** 좌석 번호 */
	@Column(name = "seat_number", nullable = false)
	private Integer seatNumber;
}