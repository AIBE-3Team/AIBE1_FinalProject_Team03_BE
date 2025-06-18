package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

/**
 * Concert Seat Entity
 * 콘서트별 좌석 정보 (가격, 등급 포함)
 */

@Entity
@Getter
@Setter
@ToString(exclude = {"concert", "seat", "tickets"})
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
	private String grade;

	@Column(precision = 10, scale = 2, nullable = false)
	private BigDecimal price;

	@OneToMany(mappedBy = "concertSeat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Ticket> tickets;
}
