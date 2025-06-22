package com.team03.ticketmon.payment.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentExecutionResponse {
	private String orderId;
	private String bookingNumber;
	private String orderName;
	private BigDecimal amount;
	private String customerName; // 예시 필드
	private String clientKey;
	private String successUrl;
	private String failUrl;
}
