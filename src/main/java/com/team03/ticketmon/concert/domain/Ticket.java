package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

/**
 * Ticket Entity
 * 티켓 정보 관리
 */

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ticket_id")
	private Long ticketId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_seat_id", nullable = false, unique = true)
	private ConcertSeat concertSeat;

	@Column(name = "ticket_number", nullable = false)
	private String ticketNumber;

	@Column(precision = 10, scale = 2, nullable = false)
	private BigDecimal price;
}
