package com.team03.ticketmon.booking.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.team03.ticketmon._global.entity.BaseTimeEntity;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.payment.domain.entity.Payment;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Booking Entity
 * 예매 정보 관리
 */

@Entity
@Table(name = "bookings")
@Builder
@Getter
@Setter
@ToString(exclude = {"concert", "tickets", "payment"})
@EqualsAndHashCode(of = "bookingNumber", callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Booking extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "booking_id")
	private Long bookingId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_id", nullable = false)
	private Concert concert;

	@Column(name = "booking_number", nullable = false, unique = true)
	private String bookingNumber;

	@Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(length = 20, nullable = false)
	private BookingStatus status;

	@Builder.Default
	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Ticket> tickets = new ArrayList<>();

	@OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Payment payment;

	/**
	 * 예매 상태를 '확정'으로 변경
	 */
	public void confirm() {
		this.status = BookingStatus.CONFIRMED;
	}

	/**
	 * 예매 상태를 '취소'로 변경
	 */
	public void cancel() {
		this.status = BookingStatus.CANCELED;
	}

	/**
	 * 예매 상태를 '결제 대기'로 변경
	 */
	public void pending() {
		this.status = BookingStatus.PENDING_PAYMENT;
	}

	/**
	 * Booking과 Ticket 간의 양방향 관계를 설정
	 * 이 메서드는 주로 createBooking 메서드 내에서 호출되어 Booking 객체 생성 시 티켓 목록을 설정
	 * 부분 취소/환불과 같은 비즈니스 로직에 따라 티켓 목록이 변경될 때도 사용될 수 있음
	 * @param tickets Booking에 연결할 Ticket 목록
	 */
	public void setTickets(List<Ticket> tickets) {
		this.tickets.clear(); // 기존 컬렉션을 지우고 새롭게 추가 (orphanRemoval 발동)
		if (tickets != null) {
			this.tickets.addAll(tickets);
			for (Ticket ticket : tickets) {
				ticket.setBooking(this);
			}
		}
	}

	/**
	 * Booking과 Payment 간의 양방향 관계를 설정하는 헬퍼 메서드
	 * @param payment 이 Booking에 연결할 Payment 엔티티
	 */
	public void setPayment(Payment payment) {
		this.payment = payment;
	}

	/**
	 * 예매의 총 금액과 상태를 업데이트
	 * 부분 취소/환불과 같은 시나리오에서 사용될 수 있음
	 * @param newAmount 새로 계산된 총 금액
	 * @param newStatus 변경할 예매 상태
	 */
	public void updateTotalAmountAndStatus(BigDecimal newAmount, BookingStatus newStatus) {
		this.totalAmount = newAmount;
		this.status = newStatus;
	}

	/**
	 * 새로운 Booking 엔티티를 생성하는 정적 팩토리 메서드
	 * 이 메서드를 통해 Booking 객체의 생성 규칙과 초기 상태를 강제
	 * @param userId 예매를 생성하는 사용자 ID
	 * @param concert 예매 대상 콘서트
	 * @param selectedSeats 선택된 콘서트 좌석 목록
	 * @return 생성된 Booking 엔티티
	 */
	public static Booking createBooking(Long userId, Concert concert, List<ConcertSeat> selectedSeats) {
		// 1. 선택된 좌석들로 Ticket들을 생성하고, Booking과의 관계 설정
		List<Ticket> tickets = selectedSeats.stream()
			.map(Ticket::createTicket)
			.toList();

		// 2. Booking 뼈대 생성
		Booking booking = Booking.builder()
			.userId(userId)
			.concert(concert)
			.bookingNumber(UUID.randomUUID().toString())
			.status(BookingStatus.PENDING_PAYMENT)
			.totalAmount(tickets.stream()
				.map(Ticket::getPrice)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
			)
			.build();

		// 3. 생성된 Booking에 Ticket 목록 설정 (양방향 관계 확립)
		booking.setTickets(tickets);
		return booking;
	}

	/**
	 * Helper to clear all associated tickets and break bidirectional links,
	 * triggering orphanRemoval for tickets and releasing concertSeat linkage.
	 */
	public void removeAllTickets() {
		for (Ticket ticket : new ArrayList<>(tickets)) {
			ticket.setBooking(null);
			if (ticket.getConcertSeat() != null) {
				ticket.getConcertSeat().releaseTicket();
			}
		}
		tickets.clear();
	}
}
