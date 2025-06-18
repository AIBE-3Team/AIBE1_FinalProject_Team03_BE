package com.team03.ticketmon.payment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "toss")
public record TossPaymentsProperties(
	@NotBlank String clientKey,
	@NotBlank String secretKey
) {}