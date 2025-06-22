package com.team03.ticketmon.payment.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;

import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentHistoryDto;
import com.team03.ticketmon.payment.dto.PaymentRequest;
import com.team03.ticketmon.payment.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller // 💡 [핵심 수정] @RestController -> @Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentApiController {

	private final PaymentService paymentService;

	// 💡 [핵심 수정] JSON 응답을 위해 @ResponseBody 추가
	@PostMapping("/request")
	@ResponseBody
	public ResponseEntity<PaymentExecutionResponse> requestPayment(
		@Valid @RequestBody PaymentRequest paymentRequest) {

		PaymentExecutionResponse response = paymentService.initiatePayment(paymentRequest);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/success")
	public String handlePaymentSuccess(
		@RequestParam String paymentKey,
		@RequestParam String orderId,
		@RequestParam BigDecimal amount) {

		log.info("결제 성공 리다이렉트 수신: paymentKey={}, orderId={}", paymentKey, orderId);

		try {
			PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
				.paymentKey(paymentKey)
				.orderId(orderId)
				.amount(amount)
				.build();

			paymentService.confirmPayment(confirmRequest);

			// 💡 [핵심 수정] "redirect:" 접두사를 붙인 문자열을 반환
			return "redirect:/payment/result/success?orderId=" + orderId;

		} catch (Exception e) {
			log.error("결제 승인 처리 중 오류 발생: orderId={}, error={}", orderId, e.getMessage());
			String encodedMessage = UriUtils.encode(e.getMessage(), StandardCharsets.UTF_8);
			// 💡 [핵심 수정] "redirect:" 접두사를 붙인 문자열을 반환
			return "redirect:/payment/result/fail?orderId=" + orderId + "&message=" + encodedMessage;
		}
	}

	// 💡 [핵심 수정] 반환 타입을 RedirectView에서 String으로 변경
	@GetMapping("/fail")
	public String handlePaymentFail(
		@RequestParam String code,
		@RequestParam String message,
		@RequestParam String orderId) {

		log.warn("결제 실패 리다이렉트 수신: orderId={}, code={}, message={}", orderId, code, message);
		paymentService.handlePaymentFailure(orderId, code, message);

		String encodedMessage = UriUtils.encode(message, StandardCharsets.UTF_8);
		// 💡 [핵심 수정] "redirect:" 접두사를 붙인 문자열을 반환
		return "redirect:/payment/result/fail?orderId=" + orderId + "&code=" + code + "&message=" + encodedMessage;
	}

	// 💡 [핵심 수정] JSON 응답을 위해 @ResponseBody 추가
	@PostMapping("/{orderId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> cancelPayment(
		@PathVariable String orderId,
		@Valid @RequestBody PaymentCancelRequest cancelRequest) {

		paymentService.cancelPayment(orderId, cancelRequest);
		return ResponseEntity.ok().build();
	}

	// 💡 [핵심 수정] JSON 응답을 위해 @ResponseBody 추가
	@GetMapping("/history")
	@ResponseBody
	public ResponseEntity<List<PaymentHistoryDto>> getPaymentHistory() {
		// TODO: 실제로는 Spring Security의 @AuthenticationPrincipal 등으로 현재 로그인된 사용자 ID를 가져와야 합니다.
		Long currentUserId = 1L; // 임시 사용자 ID
		List<PaymentHistoryDto> history = paymentService.getPaymentHistoryByUserId(currentUserId);
		return ResponseEntity.ok(history);
	}
}

// 	@GetMapping("/history")
// 	@ResponseBody
// 	public ResponseEntity<List<PaymentHistoryDto>> getPaymentHistory(
// 		@AuthenticationPrincipal CustomUserDetails userDetails) { // 💡 핵심: 로그인 사용자 정보 주입
//
// 		// 비로그인 사용자가 요청한 경우 예외 처리
// 		if (userDetails == null) {
// 			// 401 Unauthorized 응답 반환
// 			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
// 		}
//
// 		// 💡 핵심: 하드코딩된 ID 대신, 현재 로그인된 사용자의 ID를 동적으로 가져옴
// 		Long currentUserId = userDetails.getUserId();
//
// 		List<PaymentHistoryDto> history = paymentService.getPaymentHistoryByUserId(currentUserId);
// 		return ResponseEntity.ok(history);
// 	}
// }

