package com.team03.ticketmon.concert.domain;

import com.team03.ticketmon.booking.domain.Ticket;
import com.team03.ticketmon.concert.domain.enums.SeatGrade;
import com.team03.ticketmon.venue.domain.Seat;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Concert Seat Entity
 * 콘서트별 좌석 정보 (가격, 등급 포함)
 */

@Entity
@Getter
@Setter
@Table(name = "concertSeats")
@ToString(exclude = {"concert", "seat", "ticket"})
@EqualsAndHashCode(of = "concertSeatId")  // ID 기반으로 변경
@NoArgsConstructor
@AllArgsConstructor
public class ConcertSeat {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "concert_seat_id")
	private Long concertSeatId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_id", nullable = false)
	private Concert concert;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "seat_id", nullable = false)
	private Seat seat;

	@Column(length = 50, nullable = false)
	private SeatGrade grade;

	@Column(precision = 10, scale = 2, nullable = false)
	private BigDecimal price;

	@OneToOne(mappedBy = "concertSeat")
	private Ticket ticket;
}
