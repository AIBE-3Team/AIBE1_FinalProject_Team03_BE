package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * Booking Entity
 * 예매 정보 관리
 */

@Entity
@Table(name = "bookings")
@Data
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

	@Column(name = "booking_number", nullable = false)
	private String bookingNumber;

	@Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
	private BigDecimal totalAmount;

	@Column(length = 20, nullable = false)
	private String status;

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Ticket> tickets;
}
