package com.team03.ticketmon.booking.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.team03.ticketmon._global.entity.BaseTimeEntity;
import com.team03.ticketmon.concert.domain.Concert;
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
import lombok.*;

/**
 * Booking Entity
 * 예매 정보 관리
 */

@Builder
@Entity
@Getter
@Table(name = "bookings")
@ToString(exclude = {"concert", "tickets", "payment"})
@EqualsAndHashCode(of = "bookingId", callSuper = false)
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

	@Enumerated(EnumType.STRING) // Enum 타입으로 매핑
	@Column(length = 20, nullable = false)
	private BookingStatus status; // String -> BookingStatus (Enum)

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private List<Ticket> tickets = new ArrayList<>(); // NPE 방지를 위한 초기화

	@OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Payment payment;

	public void setTickets(List<Ticket> tickets) {

		// [방어 코드] 현재는 실행되지 않지만, 미래의 변경에 대비
		if (this.tickets != null) {
			this.tickets.forEach(ticket -> ticket.setBooking(null));
		}
		this.tickets = tickets;
		for (Ticket ticket : tickets) {
			ticket.setBooking(this);
		}
	}

	// 캡슐화를 위한 상태 변경 메소드 추가
	public void confirm() {
		this.status = BookingStatus.CONFIRMED;
	}

	public void cancel() {
		this.status = BookingStatus.CANCELED;
	}

	public void pending() {
		this.status = BookingStatus.PENDING_PAYMENT;
	}
}
