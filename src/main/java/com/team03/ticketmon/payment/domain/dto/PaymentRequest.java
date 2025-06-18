package com.team03.ticketmon.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class PaymentRequest {
	@NotBlank(message = "예매 번호는 필수입니다.")
	private String bookingNumber; // 결제할 대상을 예매 번호로 식별
}
