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
 * Booking Entity
 * 예매 정보 관리
 */

@Entity
@Getter
@Setter
@ToString(exclude = {"concert", "tickets"})
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

	@Enumerated(EnumType.STRING)
	@Column(length = 20, nullable = false)
	private BookingStatus status = BookingStatus.PENDING;

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Ticket> tickets;

	public enum BookingStatus {
		PENDING,     // 결제 대기중
		CONFIRMED,   // 결제 완료/예약 확정
		CANCELLED,   // 예약 취소
		REFUNDED     // 환불 완료
	}
}
