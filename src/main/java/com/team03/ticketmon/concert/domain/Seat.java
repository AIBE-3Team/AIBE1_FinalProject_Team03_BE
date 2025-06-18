package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * Seat Entity
 * 좌석 정보 관리
 */

@Entity
@Table(name = "seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seat {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "seat_id")
	private Long seatId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "venue_id", nullable = false)
	private Venue venue;

	@Column(length = 20, nullable = false)
	private String section;

	@Column(name = "seat_row", length = 10, nullable = false)
	private String seatRow;

	@Column(name = "seat_number", nullable = false)
	private Integer seatNumber;

	@OneToMany(mappedBy = "seat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<ConcertSeat> concertSeats;
}
