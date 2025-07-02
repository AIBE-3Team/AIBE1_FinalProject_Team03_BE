package com.team03.ticketmon.payment.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;

import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentHistoryDto;
import com.team03.ticketmon.payment.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Payment API", description = "결제 콜백, 내역 조회 관련 API")
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentApiController {

	private final PaymentService paymentService;

	// ==========================================================================================
	// 💡 [중요] /request, /pending-bookings, /cancel API는 BookingController로 기능이 이전/통합되었으므로 삭제합니다.
	// ==========================================================================================

	@Operation(summary = "결제 성공 콜백", description = "토스페이먼츠 결제 성공 시 리다이렉트되는 API (클라이언트 직접 호출 X)", hidden = true)
	@GetMapping("/success")
	public String handlePaymentSuccess(@RequestParam String paymentKey, @RequestParam String orderId,
		@RequestParam BigDecimal amount) {
		log.info("결제 성공 리다이렉트 수신: paymentKey={}, orderId={}", paymentKey, orderId);
		try {
			PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
				.paymentKey(paymentKey).orderId(orderId).amount(amount).build();
			paymentService.confirmPayment(confirmRequest);
			String reactSuccessUrl = "https://localhost:3000/payment/result/success";
			return "redirect:" + reactSuccessUrl + "?orderId=" + orderId;
		} catch (Exception e) {
			log.error("결제 승인 처리 중 오류 발생: orderId={}, error={}", orderId, e.getMessage());
			String encodedMessage = UriUtils.encode(e.getMessage(), StandardCharsets.UTF_8);
			String reactFailUrl = "https://localhost:3000/payment/result/fail";
			return "redirect:" + reactFailUrl + "?orderId=" + orderId + "&message=" + encodedMessage;
		}
	}

	@Operation(summary = "결제 실패 콜백", description = "토스페이먼츠 결제 실패 시 리다이렉트되는 API (클라이언트 직접 호출 X)", hidden = true)
	@GetMapping("/fail")
	public String handlePaymentFail(@RequestParam String code, @RequestParam String message,
		@RequestParam String orderId) {
		log.warn("결제 실패 리다이렉트 수신: orderId={}, code={}, message={}", orderId, code, message);
		paymentService.handlePaymentFailure(orderId, code, message);
		String encodedMessage = UriUtils.encode(message, StandardCharsets.UTF_8);
		String reactFailUrl = "https://localhost:3000/payment/result/fail";
		return "redirect:" + reactFailUrl + "?orderId=" + orderId + "&code=" + code + "&message=" + encodedMessage;
	}

	@Operation(summary = "결제 내역 조회", description = "현재 로그인된 사용자의 모든 결제 내역을 조회합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공"),
		@ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
	})
	@GetMapping("/history")
	@ResponseBody
	public ResponseEntity<List<PaymentHistoryDto>> getPaymentHistory(
		@Parameter(hidden = true) Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()
			|| !(authentication.getPrincipal() instanceof CustomUserDetails)) {
			throw new AccessDeniedException("접근 권한이 없습니다: 사용자 정보가 필요합니다.");
		}
		CustomUserDetails userDetails = (CustomUserDetails)authentication.getPrincipal();
		List<PaymentHistoryDto> history = paymentService.getPaymentHistoryByUserId(userDetails.getUserId());
		return ResponseEntity.ok(history);
	}
}
