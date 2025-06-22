package com.team03.ticketmon.payment.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_cancel_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancelHistory {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long cancelHistoryId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id", nullable = false)
	private Payment payment;

	@Column(nullable = false, unique = true, length = 64)
	private String transactionKey;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal cancelAmount;

	@Column(nullable = false, length = 200)
	private String cancelReason;

	@Column(nullable = false)
	private LocalDateTime canceledAt;

	// ... 환불 계좌 정보 필드들 ...

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	private void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	@Builder
	public PaymentCancelHistory(Payment payment, String transactionKey, BigDecimal cancelAmount, String cancelReason,
		LocalDateTime canceledAt) {
		this.payment = payment;
		this.transactionKey = transactionKey;
		this.cancelAmount = cancelAmount;
		this.cancelReason = cancelReason;
		this.canceledAt = canceledAt;
	}
}
