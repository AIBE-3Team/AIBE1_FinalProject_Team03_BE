package com.team03.ticketmon.payment.controller;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/toss")
@RequiredArgsConstructor
public class WebhookController {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final PaymentService paymentService;

	@PostMapping("/payment-updates")
	public ResponseEntity<String> handleTossPaymentWebhook(@RequestBody String payload) {
		log.info("토스페이먼츠 웹훅 수신: {}", payload);
		try {
			JsonNode jsonNode = objectMapper.readTree(payload);
			String eventType = jsonNode.get("eventType").asText();

			// 가상계좌 입금 완료 등 특정 이벤트만 처리
			if ("PAYMENT_STATUS_CHANGED".equals(eventType)) {
				JsonNode data = jsonNode.get("data");
				String orderId = data.get("orderId").asText();
				String status = data.get("status").asText(); // DONE, CANCELED 등
				paymentService.updatePaymentStatusByWebhook(orderId, status);
			}
			// (보안) 실제 운영에서는 웹훅 서명 검증 로직이 추가되어야 합니다.

			return ResponseEntity.ok("Webhook processed successfully.");
		} catch (IOException e) {
			log.error("웹훅 페이로드 파싱 실패: {}", e.getMessage(), e);
			// 실패하더라도 토스 서버에는 2xx 응답을 보내야 재전송을 막을 수 있습니다.
			return ResponseEntity.ok("Webhook payload parsing error, but acknowledged.");
		}
	}
}
