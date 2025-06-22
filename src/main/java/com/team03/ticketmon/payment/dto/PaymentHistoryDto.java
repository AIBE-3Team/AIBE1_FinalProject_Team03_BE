package com.team03.ticketmon.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;

import lombok.Getter;

@Getter
public class PaymentHistoryDto {
	private String bookingNumber;
	private String orderId;
	private String orderName;
	private BigDecimal amount;
	private PaymentStatus paymentStatus;
	private LocalDateTime approvedAt;
	private String paymentMethod;

	public PaymentHistoryDto(Payment payment) {
		this.bookingNumber = payment.getBooking().getBookingNumber();
		this.orderId = payment.getOrderId();
		this.orderName = payment.getBooking().getConcert().getTitle();
		this.amount = payment.getAmount();
		this.paymentStatus = payment.getStatus();
		this.approvedAt = payment.getApprovedAt();
		this.paymentMethod = payment.getPaymentMethod();
	}
}
