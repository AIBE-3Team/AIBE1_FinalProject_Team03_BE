package com.team03.ticketmon.concert.domain;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.booking.domain.Ticket;
import com.team03.ticketmon.concert.domain.enums.SeatGrade;
import com.team03.ticketmon.venue.domain.Seat;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Concert Seat Entity
 * 콘서트별 좌석 정보 (가격, 등급 포함)
 */

@Entity
@Table(name = "concertSeats")
@Getter
@ToString(exclude = {"concert", "seat", "ticket"})
@EqualsAndHashCode(of = {"concertSeatId"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

	/**
	 * ConcertSeat 엔티티를 생성하는 정적 팩토리 메서드
	 * 콘서트 좌석의 모든 필수 정보를 받아 유효한 객체를 생성하도록 강제
	 * @param concert 이 좌석이 속할 콘서트
	 * @param seat 실제 좌석 정보
	 * @param grade 좌석 등급
	 * @param price 좌석 가격
	 * @return 생성된 ConcertSeat 엔티티
	 */
	public static ConcertSeat create(Concert concert, Seat seat, SeatGrade grade, BigDecimal price) {
		if (concert == null || seat == null || grade == null || price == null) {
			throw new IllegalArgumentException("ConcertSeat 필수 필드는 null일 수 없습니다.");
		}
		return new ConcertSeat(null, concert, seat, grade, price, null);
	}

	/**
	 * ConcertSeat에 Ticket을 설정하거나 해제하는 메서드
	 * Ticket과의 양방향 관계를 올바르게 관리하며, 좌석에 이미 티켓이 할당된 경우 중복 할당을 방지
	 * @param ticket 이 ConcertSeat에 연결될 Ticket 엔티티 (null은 티켓 해제를 의미)
	 */
	public void setTicket(Ticket ticket) {
		if (this.ticket != null && ticket != null && !this.ticket.equals(ticket)) {
			throw new BusinessException(ErrorCode.SERVER_ERROR, "좌석에 이미 티켓이 할당됨.");
		}
		this.ticket = ticket;
	}

	/**
	 * ConcertSeat에 할당된 Ticket을 해제
	 * 주로 티켓 취소 시 좌석을 다시 AVAILABLE 상태로 만들기 위해 사용
	 */
	public void releaseTicket() {
		this.ticket = null;
	}
}
