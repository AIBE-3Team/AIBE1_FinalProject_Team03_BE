package com.team03.ticketmon.payment.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.team03.ticketmon.concert.domain.Booking;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments") // ERD에 정의된 'payments' 테이블
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long paymentId;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id", nullable = false, unique = true)
	private Booking booking; // **핵심: 기존 Booking 엔티티와 1:1 연관관계**

	@Column(nullable = false, unique = true, length = 64)
	private String orderId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(unique = true, length = 200)
	private String paymentKey;

	@Column(length = 50)
	private String paymentMethod;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	private LocalDateTime approvedAt;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	@Builder
	public Payment(Booking booking, String orderId, BigDecimal amount) {
		this.booking = booking;
		this.orderId = orderId;
		this.amount = amount;
		this.status = PaymentStatus.PENDING;
	}

	/**
	 * 결제 승인이 완료되었을 때 호출됩니다.
	 * paymentKey를 저장하고, 상태를 DONE으로 변경하며, 승인 시간을 기록합니다.
	 * @param paymentKey 토스페이먼츠로부터 받은 결제 키
	 */
	public void complete(String paymentKey) {
		this.paymentKey = paymentKey;
		this.status = PaymentStatus.DONE;
		this.approvedAt = LocalDateTime.now();
	}

	/**
	 * 결제가 실패했을 때 호출됩니다.
	 * 상태를 FAILED로 변경합니다.
	 */
	public void fail() {
		this.status = PaymentStatus.FAILED;
	}

	public void cancel() {
		this.status = PaymentStatus.CANCELED;
	}
}

