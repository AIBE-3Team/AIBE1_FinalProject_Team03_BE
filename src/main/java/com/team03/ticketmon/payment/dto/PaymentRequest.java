package com.team03.ticketmon.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequest {
	@NotBlank(message = "예매 번호는 필수입니다.")
	private String bookingNumber; // 결제할 대상을 예매 번호로 식별
}
