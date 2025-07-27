package com.team03.ticketmon.payment.controller;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon._global.exception.BusinessException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Payment API", description = "결제 관련 API")
@Controller
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentApiController {

    private final PaymentService paymentService;
    private final AppProperties appProperties;
    private static final long TIMEOUT_MS = 10_000L;

    // ==========================================================================================
    // /request, /pending-bookings, /cancel API는 BookingController로 기능이 이전/통합되었으므로 삭제합니다.
    // ==========================================================================================

    @Operation(
            summary = "결제 성공 콜백 (non-blocking)",
            description = "토스페이먼츠가 리다이렉트하는 결제 성공 URL을 비동기로 처리합니다.",
            hidden = true
    )
    @GetMapping("/success")
    public DeferredResult<String> handlePaymentSuccess(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam BigDecimal amount,
            @RequestParam(name = "originalMethod", required = false, defaultValue = "카드") String originalMethod
    ) {
        log.info("결제 성공 콜백 수신: paymentKey={}, orderId={}, amount={}, originalMethod={}",
                paymentKey, orderId, amount, originalMethod);

        // 1) DeferredResult 생성 (서블릿 쓰레드 즉시 반환)
        DeferredResult<String> dr = new DeferredResult<>(TIMEOUT_MS);

        // 2) 기존 DTO 빌드
        PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .amount(amount)
                .originalMethod(originalMethod)
                .build();

        // 3) Reactive 흐름으로 기존 비즈니스 로직 실행
        paymentService.confirmPayment(confirmRequest)
                // 4) 성공 콜백: DB에서 bookingNumber 조회 → redirect URL 생성
                .doOnSuccess(v -> {
                    String bookingNumber = paymentService.getBookingNumberByOrderId(orderId);
                    String base = appProperties.frontBaseUrl() + "/payment/result/success";
                    String url = base +
                            "?orderId=" + UriUtils.encode(orderId, StandardCharsets.UTF_8) +
                            "&bookingNumber=" + UriUtils.encode(bookingNumber, StandardCharsets.UTF_8);
                    dr.setResult("redirect:" + url);
                })
                // 5) 에러 콜백: 실패 페이지로 redirect
                .doOnError(e -> {
                    log.error("결제 승인 처리 중 오류: {}", e.getMessage(), e);
                    String msg = (e instanceof BusinessException)
                            ? e.getMessage()
                            : "결제 처리 중 오류가 발생했습니다.";
                    String failBase = appProperties.frontBaseUrl() + "/payment/result/fail";
                    String url = failBase +
                            "?orderId=" + UriUtils.encode(orderId, StandardCharsets.UTF_8) +
                            "&message=" + UriUtils.encode(msg, StandardCharsets.UTF_8);
                    dr.setResult("redirect:" + url);
                })
                // 6) 실제 구독 시작
                .subscribe();

        return dr;
    }

    @Operation(summary = "결제 실패 콜백", description = "토스페이먼츠 결제 실패 시 리다이렉트되는 API", hidden = true)
    @GetMapping("/fail")
    public String handlePaymentFail(@RequestParam String code, @RequestParam String message,
                                    @RequestParam String orderId) {
        log.warn("결제 실패 리다이렉트 수신: orderId={}, code={}, message={}", orderId, code, message);
        paymentService.handlePaymentFailure(orderId, code, message);
        String encodedMessage = UriUtils.encode(message, StandardCharsets.UTF_8);
        String Url = appProperties.frontBaseUrl() + "/payment/result/fail" + "?orderId=" + orderId + "&code=" + code + "&message=" + encodedMessage;
        return "redirect:" + Url;
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
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        List<PaymentHistoryDto> history = paymentService.getPaymentHistoryByUserId(userDetails.getUserId());
        return ResponseEntity.ok(history);
    }
}
