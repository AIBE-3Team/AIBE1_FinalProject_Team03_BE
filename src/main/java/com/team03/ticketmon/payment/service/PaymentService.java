package com.team03.ticketmon.payment.service;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import com.team03.ticketmon.booking.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.entity.PaymentCancelHistory;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentHistoryDto;
import com.team03.ticketmon.payment.repository.PaymentCancelHistoryRepository;
import com.team03.ticketmon.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j // 로그 출력을 위한 Lombok 어노테이션
@Service // Spring의 서비스 빈 등록
@RequiredArgsConstructor // 생성자 주입 자동 생성
public class PaymentService {

	// 의존성 주입: 결제/예매/취소 이력/환경설정/외부 API 호출 등에 필요한 객체들
	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelHistoryRepository paymentCancelHistoryRepository;
	private final TossPaymentsProperties tossPaymentsProperties;
	private final AppProperties appProperties;
	private final WebClient webClient; // 비동기 HTTP 통신용

	/**
	 * 결제 준비(결제 정보 생성 또는 재사용)
	 * - 예매 번호로 예매 정보 조회
	 * - 이미 결제(PENDING) 정보가 있으면 재사용, 없으면 새로 생성
	 * - 결제 준비에 필요한 정보(PaymentExecutionResponse) 반환
	 */
	@Transactional
	public PaymentExecutionResponse initiatePayment(Booking booking) {

		if (booking == null) {
			throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
		}

		// 1. 예매 상태가 결제 대기(PENDING_PAYMENT)인지 확인
		if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
			throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS_FOR_PAYMENT);
		}

		// 2. 기존 결제 정보(PENDING) 있으면 재사용, 없으면 새로 생성
		Optional<Payment> existingPaymentOpt = paymentRepository.findByBooking(booking)
			.filter(p -> p.getStatus() == PaymentStatus.PENDING);

		Payment paymentToUse;

		if (existingPaymentOpt.isPresent()) {
			paymentToUse = existingPaymentOpt.get();
			log.info("기존 결제 정보를 재사용합니다. orderId: {}", paymentToUse.getOrderId());
		} else {
			log.info("신규 결제 정보를 생성합니다. bookingNumber: {}", booking.getBookingNumber());
			String orderId = UUID.randomUUID().toString(); // 고유 주문 ID 생성
			paymentToUse = paymentRepository.save(Payment.builder()
				.booking(booking)
				.orderId(orderId)
				.amount(booking.getTotalAmount())
				.build());
		}

		// 3. 결제 준비 응답 객체 생성 및 반환
		return PaymentExecutionResponse.builder()
			.orderId(paymentToUse.getOrderId())
			.bookingNumber(booking.getBookingNumber())
			.orderName(booking.getConcert().getTitle())
			.amount(booking.getTotalAmount())
			.customerName(booking.getUserId().toString())
			.clientKey(tossPaymentsProperties.clientKey())
			.successUrl(appProperties.baseUrl() + "/api/v1/payments/success")
			.failUrl(appProperties.baseUrl() + "/api/v1/payments/fail")
			.build();
	}

	/**
	 * 결제 승인 처리
	 * - 결제 승인 요청이 들어오면, 금액 검증 및 외부(PG사) 결제 승인 API 호출
	 * - 승인 성공 시 결제/예매 상태 갱신
	 */
	@Transactional
	public void confirmPayment(PaymentConfirmRequest confirmRequest) {
		// 1. 결제 정보 조회
		Payment payment = paymentRepository.findByOrderId(confirmRequest.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + confirmRequest.getOrderId()));

		// 2. 결제 금액 검증(DB와 요청 값 비교)
		log.info("금액 검증 시작: DB 금액 = {}, 요청 금액 = {}", payment.getAmount(), confirmRequest.getAmount());
		if (payment.getAmount().compareTo(confirmRequest.getAmount()) != 0) {
			log.error("결제 금액 불일치 오류! DB 금액: {}, 요청 금액: {}", payment.getAmount(), confirmRequest.getAmount());
			throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
		}

		// 3. 결제 상태가 이미 처리된 경우 중복 승인 방지
		if (payment.getStatus() != PaymentStatus.PENDING) {
			log.warn("Payment with orderId {} is already processed (status: {}). Ignoring duplicate request.",
				confirmRequest.getOrderId(), payment.getStatus());
			return;
		}

		// 4. 토스페이먼츠 API 호출을 위한 인증키 준비
		String encodedSecretKey = Base64.getEncoder()
			.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
		String idempotencyKey = confirmRequest.getOrderId(); // 멱등성 키(중복 요청 방지)

		// 5. 외부 결제 승인 API 호출 및 결과 처리
		callTossConfirmApi(confirmRequest, encodedSecretKey, idempotencyKey)
			.doOnSuccess(tossResponse -> {
				// 5-1. 결제 승인 성공 시 결제 정보 갱신
				String paymentKey = (String)tossResponse.get("paymentKey");
				LocalDateTime approvedAt = parseDateTime(tossResponse.get("approvedAt"));

				payment.complete(paymentKey, approvedAt); // 결제 완료 처리
				payment.getBooking().confirm(); // 예매도 확정
				paymentRepository.save(payment); // 명시적 저장
				bookingRepository.save(payment.getBooking());
				log.info("결제 승인 완료: orderId={}", payment.getOrderId());
			})
			.doOnError(e -> {
				// 5-2. 결제 승인 실패 시 상태 갱신 및 예외 발생
				log.error("결제 승인 API 호출 중 오류 발생: orderId={}, 오류={}", confirmRequest.getOrderId(), e.getMessage());
				payment.fail();
				paymentRepository.save(payment);
				throw new RuntimeException("결제 승인에 실패했습니다.", e);
			})
			.block(); // Mono(비동기) -> 동기 처리로 변환(실행)
	}

	/**
	 * 토스페이먼츠 결제 승인 API 호출 (비동기)
	 * - 멱등성 키 사용
	 */
	private Mono<Map> callTossConfirmApi(PaymentConfirmRequest confirmRequest, String encodedSecretKey,
		String idempotencyKey) {
		Map<String, Object> requestBody = Map.of(
			"paymentKey", confirmRequest.getPaymentKey(),
			"orderId", confirmRequest.getOrderId(),
			"amount", confirmRequest.getAmount()
		);

		log.info("Calling Toss Payments Confirm API with Idempotency-Key: {}", idempotencyKey);
		return webClient.post()
			.uri("https://api.tosspayments.com/v1/payments/confirm")
			.header("Authorization", "Basic " + encodedSecretKey)
			.header("Idempotency-Key", idempotencyKey) // 중복 요청 방지
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(requestBody)
			.retrieve()
			.onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
				.flatMap(errorBody -> {
					log.error("토스페이먼츠 API 에러: status={}, body={}", response.statusCode(), errorBody);
					return Mono.error(new RuntimeException("토스페이먼츠 API 호출 실패: " + errorBody));
				}))
			.bodyToMono(Map.class);
	}

	/**
	 * 결제 실패 처리
	 * - 결제 실패 시 결제/예매 상태를 실패/취소로 변경
	 */
	@Transactional
	public void handlePaymentFailure(String orderId, String errorCode, String errorMessage) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId));

		// 결제 상태가 PENDING일 때만 실패 처리
		if (payment.getStatus() == PaymentStatus.PENDING) {
			payment.fail();
			payment.getBooking().cancel(); // 예매도 취소
			paymentRepository.save(payment);
			bookingRepository.save(payment.getBooking());
			log.info("결제 실패 상태로 변경 완료: orderId={}, errorCode={}, errorMessage={}", orderId, errorCode, errorMessage);
		} else {
			log.warn("이미 처리된 주문에 대한 실패 처리 요청: orderId={}, 현재 상태: {}", orderId, payment.getStatus());
		}
	}

	/**
	 * 전달받은 Payment 엔티티에 대해 외부 결제 API를 통해 환불을 실행하고 상태를 변경
	 * 이 메서드는 상위 서비스(Facade)에서 모든 비즈니스 유효성 검사를 마친 후 호출되어야 한다.
	 *
	 * @param booking 취소할 Payment 엔티티
	 * @param reason 취소 사유
	 */
	@Transactional
	public void cancelPayment(Booking booking, String reason) {

		paymentRepository.findByBooking(booking).ifPresent(payment -> {
			switch (payment.getStatus()) {
				case DONE   -> internalCancel(payment, reason);   // 환불
				case PENDING -> payment.fail();                   // 결제 미완료면 실패 처리
				default -> log.info("취소 불필요 – status={}", payment.getStatus());
			}
		});
	}
	private void internalCancel(Payment payment, String reason) {

		// 토스페이먼츠 API 인증키 준비
		String encodedSecretKey = Base64.getEncoder()
				.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

		// 외부 결제 취소 API 호출
		callTossCancelApi(payment.getPaymentKey(), reason, encodedSecretKey)
				.doOnSuccess(tossResponse -> {
					// 결제/예매 상태 취소로 변경
					payment.cancel();

					// 취소 응답에서 취소 이력 정보 추출
					String transactionKey = null;
					BigDecimal cancelAmount = BigDecimal.ZERO;
					LocalDateTime canceledAt = null;

					List<Map<String, Object>> cancels = (List<Map<String, Object>>)tossResponse.get("cancels");
					if (cancels != null && !cancels.isEmpty()) {
						Map<String, Object> lastCancel = cancels.get(cancels.size() - 1);
						transactionKey = (String)lastCancel.get("transactionKey");

						Object amountObj = lastCancel.get("cancelAmount");
						if (amountObj instanceof Integer) {
							cancelAmount = BigDecimal.valueOf((Integer)amountObj);
						} else if (amountObj instanceof Double) {
							cancelAmount = BigDecimal.valueOf((Double)amountObj);
						} else if (amountObj != null) {
							cancelAmount = new BigDecimal(amountObj.toString());
						}

						Object canceledAtObj = lastCancel.get("canceledAt");
						if (canceledAtObj instanceof String) {
							try {
								canceledAt = LocalDateTime.parse((String)canceledAtObj,
										DateTimeFormatter.ISO_OFFSET_DATE_TIME);
							} catch (DateTimeParseException e) {
								log.warn("canceledAt 파싱 실패 (ISO_OFFSET_DATE_TIME): {}, 다른 포맷 시도", canceledAtObj);
								try {
									canceledAt = LocalDateTime.parse((String)canceledAtObj,
											DateTimeFormatter.ISO_DATE_TIME);
								} catch (DateTimeParseException ex) {
									log.error("canceledAt 파싱 최종 실패: {}", canceledAtObj, ex);
									canceledAt = LocalDateTime.now();
								}
							}
						}
					} else {
						log.warn("토스페이먼츠 취소 응답에 'cancels' 정보가 없거나 비어 있습니다. orderId: {}", payment.getOrderId());
					}

					if (canceledAt == null) {
						canceledAt = LocalDateTime.now();
					}

					// 결제 취소 이력 저장
					PaymentCancelHistory history = PaymentCancelHistory.builder()
							.payment(payment)
							.transactionKey(transactionKey)
							.cancelAmount(cancelAmount)
							.cancelReason(reason)
							.canceledAt(canceledAt)
							.build();
					paymentCancelHistoryRepository.save(history);

					log.info("결제 취소 완료: orderId={}", payment.getOrderId());
				})
				.doOnError(e -> {
					log.error("결제 취소 중 오류 발생: orderId={}, 오류={}", payment.getOrderId(), e.getMessage(), e);
					throw new RuntimeException("결제 취소에 실패했습니다. (내부 오류)", e);
				})
				.block();

	}

	/**
	 * 토스페이먼츠 결제 취소 API 호출 (비동기)
	 */
	private Mono<Map> callTossCancelApi(String paymentKey, String cancelReason, String encodedSecretKey) {
		log.info("Calling Toss Payments Cancel API for paymentKey: {}", paymentKey);
		return webClient.post()
			.uri("https://api.tosspayments.com/v1/payments/{paymentKey}/cancel", paymentKey)
			.header("Authorization", "Basic " + encodedSecretKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("cancelReason", cancelReason))
			.retrieve()
			.onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
				.flatMap(errorBody -> {
					log.error("토스페이먼츠 취소 API 호출 실패: status={}, body={}", response.statusCode(), errorBody);
					return Mono.error(new RuntimeException("결제 취소에 실패했습니다. (토스 응답 오류): " + errorBody));
				}))
			.bodyToMono(Map.class);
	}

	/**
	 * ISO 8601 문자열을 LocalDateTime으로 변환
	 * - 토스 응답의 날짜 포맷 대응
	 */
	private LocalDateTime parseDateTime(Object dateTimeObj) {
		if (dateTimeObj instanceof String dateTimeStr) {
			try {
				return OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
			} catch (DateTimeParseException e) {
				log.warn("날짜 파싱 실패 (ISO_OFFSET_DATE_TIME): {}. 다른 포맷 시도.", dateTimeStr);
				try {
					return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
				} catch (DateTimeParseException ex) {
					log.error("날짜 파싱 최종 실패: {}", dateTimeStr, ex);
				}
			}
		}
		return LocalDateTime.now();
	}

	/**
	 * 웹훅을 통한 결제 상태 갱신
	 * - 외부 시스템(토스 등)에서 결제 상태 변경 알림이 오면 상태를 업데이트
	 */
	@Transactional
	public void updatePaymentStatusByWebhook(String orderId, String status) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> {
				log.warn("웹훅 처리: 존재하지 않는 주문 ID 입니다 - {}", orderId);
				return new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId);
			});

		PaymentStatus newStatus = PaymentStatus.valueOf(status.toUpperCase());

		// 이미 상태가 동일하면 변경 없음
		if (payment.getStatus() == newStatus) {
			log.info("웹훅 처리: 주문 ID {}의 상태가 이미 {}입니다. 변경 없음.", orderId, newStatus);
			return;
		}

		// 상태에 따라 결제/예매 상태 갱신
		if (newStatus == PaymentStatus.DONE) {
			payment.complete(payment.getPaymentKey(), LocalDateTime.now());
			payment.getBooking().confirm();
		} else if (newStatus == PaymentStatus.CANCELED) {
			payment.cancel();
			payment.getBooking().cancel();
		}

		log.info("주문 ID {} 의 결제 상태가 웹훅을 통해 {} 로 변경되었습니다.", orderId, newStatus);
	}

	/**
	 * 사용자별 결제 내역 조회
	 * - 사용자 ID로 결제 내역을 모두 조회하여 DTO로 변환 후 반환
	 */
	@Transactional(readOnly = true)
	public List<PaymentHistoryDto> getPaymentHistoryByUserId(Long userId) {
		return paymentRepository.findByBooking_UserId(userId)
			.stream()
			.map(PaymentHistoryDto::new)
			.collect(Collectors.toList());
	}
}
