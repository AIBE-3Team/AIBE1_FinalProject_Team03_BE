package com.team03.ticketmon.concert.domain;

import java.math.BigDecimal;
import java.util.List;

import com.team03.ticketmon.concert.domain.enums.BookingStatus;
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

@Builder
@Entity
@Getter
@Setter
@Table(name = "bookings")
@ToString(exclude = {"concert", "tickets", "payment"})
@EqualsAndHashCode(of = "bookingId")
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
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

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Ticket> tickets;

	@OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Payment payment;

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
