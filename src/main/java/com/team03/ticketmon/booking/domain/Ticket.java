package com.team03.ticketmon.booking.domain;

import com.team03.ticketmon._global.entity.BaseTimeEntity;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Ticket Entity
 * 티켓 정보 관리
 */

@Entity
@Table(name = "tickets")
@Getter
@ToString(exclude = {"booking", "concertSeat"})
@EqualsAndHashCode(of = "ticketId", callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ticket_id")
	private Long ticketId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_seat_id", nullable = false, unique = true)
	private ConcertSeat concertSeat;

	@Column(name = "ticket_number", nullable = false, unique = true)
	private String ticketNumber;

	@Column(precision = 10, scale = 2, nullable = false)
	private BigDecimal price;

	// == 생성자 대신 정적 팩토리 메서드 사용 == //
	public static Ticket createTicket(ConcertSeat concertSeat) {
		Ticket ticket = new Ticket();
		ticket.concertSeat = concertSeat;
		ticket.ticketNumber = java.util.UUID.randomUUID().toString(); // 고유 티켓 번호 생성
		ticket.price = concertSeat.getPrice();
		return ticket;
	}

	protected void setBooking(Booking booking) {
		this.booking = booking;
	}
}
