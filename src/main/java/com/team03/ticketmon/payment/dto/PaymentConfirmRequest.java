package com.team03.ticketmon.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentConfirmRequest {
    @NotBlank
    private String paymentKey;
    @NotBlank
    private String orderId;
    @NotNull
    private BigDecimal amount;
    @NotBlank
    private String originalMethod;  // "카드" 혹은 "간편결제"
}
