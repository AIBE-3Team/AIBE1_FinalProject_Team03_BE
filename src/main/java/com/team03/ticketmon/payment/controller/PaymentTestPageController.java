package com.team03.ticketmon.payment.controller;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.team03.ticketmon.concert.domain.Booking;
import com.team03.ticketmon.concert.domain.enums.BookingStatus;
import com.team03.ticketmon.concert.repository.BookingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/payment") // 페이지 관련 경로는 /payment로 그룹화
@Profile("dev") // 개발 환경에서만 활성화
@RequiredArgsConstructor
public class PaymentTestPageController {

	private final BookingRepository bookingRepository;

	// 결제 시작 페이지
	@GetMapping("/checkout")
	public String checkoutPage(Model model) {
		List<Booking> pendingBookings = bookingRepository.findByStatus(BookingStatus.PENDING_PAYMENT);
		if (pendingBookings.isEmpty()) {
			model.addAttribute("errorMessage", "결제 가능한 예매 내역이 없습니다. DB에 PENDING_PAYMENT 상태의 예매를 추가해주세요.");
		}
		model.addAttribute("bookings", pendingBookings);
		return "payment/checkout"; // templates/payment/checkout.html
	}

	// 결제 성공 결과 페이지
	@GetMapping("/result/success")
	public String paymentSuccessResultPage(@RequestParam String orderId, Model model) {
		model.addAttribute("orderId", orderId);
		return "payment/success"; // templates/payment/success.html
	}

	// 결제 실패 결과 페이지
	@GetMapping("/result/fail")
	public String paymentFailResultPage(@RequestParam String orderId,
		@RequestParam(required = false) String code,
		@RequestParam(required = false) String message, Model model) {
		model.addAttribute("orderId", orderId);
		model.addAttribute("errorCode", code);
		model.addAttribute("errorMessage", message);
		return "payment/fail"; // templates/payment/fail.html
	}
}
