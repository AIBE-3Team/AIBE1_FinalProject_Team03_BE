package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

import com.team03.ticketmon.concert.domain.enums.BookingStatus;

/**
 * Booking Entity
 * 예매 정보 관리
 */

@Entity
@Getter
@Setter
@Table(name = "bookings")
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

	@Enumerated(EnumType.STRING) // Enum 타입으로 매핑
	@Column(length = 20, nullable = false)
	private BookingStatus status; // String -> BookingStatus (Enum)

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Ticket> tickets;
}