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
@EqualsAndHashCode(of = "ticketNumber", callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // 정적 팩토리 메서드 사용을 위한 private 전체 필드 생성자 (Lombok 빌더가 내부적으로 사용)
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

	/**
	 * Ticket 엔티티를 생성하는 정적 팩토리 메서드
	 * Ticket 객체의 생성 규칙(고유 번호, 가격 설정, ConcertSeat와의 양방향 관계)을 캡슐화
	 * @param concertSeat 티켓이 연결될 ConcertSeat 엔티티
	 * @return 생성된 Ticket 엔티티
	 */
	public static Ticket createTicket(ConcertSeat concertSeat) {
		Ticket ticket = new Ticket();
		ticket.ticketNumber = java.util.UUID.randomUUID().toString(); // 고유 티켓 번호 생성
		ticket.price = concertSeat.getPrice();
		ticket.setConcertSeatInternal(concertSeat);
		concertSeat.setTicket(ticket);
		return ticket;
	}

	/**
	 * Ticket에 Booking을 설정하는 메서드
	 * 주로 Booking.setTickets() 메서드 내에서 호출되어 양방향 관계를 설정
	 * 외부에서 직접 호출하여 Booking을 변경하는 것을 막기 위해 protected로 제한
	 * @param booking 이 Ticket이 속할 Booking 엔티티
	 */
	protected void setBooking(Booking booking) {
		this.booking = booking;
	}

	/**
	 * Ticket에 ConcertSeat를 설정하는 내부 전용 메서드
	 * Ticket 객체 생성 시점에 단 한 번 호출되며, Ticket의 ConcertSeat는 생성 후 변경되지 않음을 강제
	 * public setter를 없애 불변성을 강화
	 * @param concertSeat 이 Ticket이 연결될 ConcertSeat 엔티티
	 */
	protected void setConcertSeatInternal(ConcertSeat concertSeat) {
		this.concertSeat = concertSeat;
	}

}
