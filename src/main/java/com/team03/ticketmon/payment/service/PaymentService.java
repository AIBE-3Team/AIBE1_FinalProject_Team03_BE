package com.team03.ticketmon.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon.concert.domain.Booking;
import com.team03.ticketmon.concert.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.entity.PaymentCancelHistory;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentHistoryDto;
import com.team03.ticketmon.payment.dto.PaymentRequest;
import com.team03.ticketmon.payment.repository.PaymentCancelHistoryRepository;
import com.team03.ticketmon.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelHistoryRepository paymentCancelHistoryRepository;
	private final TossPaymentsProperties tossPaymentsProperties;
	private final AppProperties appProperties;
	private final WebClient webClient;

	@Transactional
	public PaymentExecutionResponse initiatePayment(PaymentRequest paymentRequest) {
		Booking booking = bookingRepository.findByBookingNumber(paymentRequest.getBookingNumber())
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예매 번호입니다."));

		if (booking.getStatus() != com.team03.ticketmon.concert.domain.enums.BookingStatus.PENDING_PAYMENT) {
			throw new IllegalStateException("결제를 진행할 수 없는 예매 상태입니다.");
		}

		// 💡 [수정] 기존 결제 정보가 PENDING 상태일 때만 재사용
		Optional<Payment> existingPaymentOpt = paymentRepository.findByBooking(booking)
			.filter(p -> p.getStatus() == PaymentStatus.PENDING);
		Payment paymentToUse;
		if (existingPaymentOpt.isPresent()) {
			paymentToUse = existingPaymentOpt.get();
			log.info("기존 결제 정보를 재사용합니다. orderId: {}", paymentToUse.getOrderId());
		} else {
			log.info("신규 결제 정보를 생성합니다. bookingNumber: {}", booking.getBookingNumber());
			String orderId = UUID.randomUUID().toString();
			paymentToUse = paymentRepository.save(Payment.builder()
				.booking(booking)
				.orderId(orderId)
				.amount(booking.getTotalAmount())
				.build());
		}

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

	@Transactional
	public void confirmPayment(PaymentConfirmRequest confirmRequest) {
		Payment payment = paymentRepository.findByOrderId(confirmRequest.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + confirmRequest.getOrderId()));

		log.info("금액 검증 시작: DB 금액 = {}, 요청 금액 = {}", payment.getAmount(), confirmRequest.getAmount());
		if (payment.getAmount().compareTo(confirmRequest.getAmount()) != 0) {
			log.error("결제 금액 불일치 오류! DB 금액: {}, 요청 금액: {}", payment.getAmount(), confirmRequest.getAmount());
			throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
		}

		// 💡 [수정] 이미 PENDING 상태가 아닌 경우 중복 처리 방지
		if (payment.getStatus() != PaymentStatus.PENDING) {
			log.warn("Payment with orderId {} is already processed (status: {}). Ignoring duplicate request.",
				confirmRequest.getOrderId(), payment.getStatus());
			return;
		}

		String encodedSecretKey = Base64.getEncoder()
			.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
		String idempotencyKey = confirmRequest.getOrderId(); // 멱등성 키로 orderId 사용

		callTossConfirmApi(confirmRequest, encodedSecretKey, idempotencyKey)
			.doOnSuccess(tossResponse -> {
				String paymentKey = (String)tossResponse.get("paymentKey");
				LocalDateTime approvedAt = parseDateTime(tossResponse.get("approvedAt"));

				payment.complete(paymentKey, approvedAt);
				payment.getBooking().confirm();
				paymentRepository.save(payment); // 💡 [추가] Dirty checking 외에 명시적 save
				bookingRepository.save(payment.getBooking()); // 💡 [추가] Dirty checking 외에 명시적 save
				log.info("결제 승인 완료: orderId={}", payment.getOrderId());
			})
			.doOnError(e -> {
				log.error("결제 승인 API 호출 중 오류 발생: orderId={}, 오류={}", confirmRequest.getOrderId(), e.getMessage());
				payment.fail();
				paymentRepository.save(payment); // 💡 [추가] Dirty checking 외에 명시적 save
				// booking은 실패 시 취소되지 않으므로 save 안함
				throw new RuntimeException("결제 승인에 실패했습니다.", e);
			})
			.block();
	}

	// 💡 [수정] idempotencyKey 파라미터 추가 및 로깅
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
			.header("Idempotency-Key", idempotencyKey) // 💡 [핵심] 멱등성 키 헤더 추가
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

	@Transactional
	public void handlePaymentFailure(String orderId, String errorCode, String errorMessage) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId));

		if (payment.getStatus() == PaymentStatus.PENDING) {
			payment.fail();
			payment.getBooking().cancel(); // 예매도 취소
			paymentRepository.save(payment); // 💡 [추가] 명시적 save
			bookingRepository.save(payment.getBooking()); // 💡 [추가] 명시적 save
			log.info("결제 실패 상태로 변경 완료: orderId={}, errorCode={}, errorMessage={}", orderId, errorCode, errorMessage);
		} else {
			log.warn("이미 처리된 주문에 대한 실패 처리 요청: orderId={}, 현재 상태: {}", orderId, payment.getStatus());
		}
	}

	@Transactional
	public void cancelPayment(String orderId, PaymentCancelRequest cancelRequest) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId));

		if (payment.getStatus() == PaymentStatus.CANCELED) {
			throw new IllegalStateException("이미 취소된 결제입니다.");
		}
		if (payment.getStatus() != PaymentStatus.DONE) { // 💡 [추가] DONE 상태에서만 취소 가능하도록
			throw new IllegalStateException("결제 완료 상태에서만 취소가 가능합니다.");
		}

		String encodedSecretKey = Base64.getEncoder()
			.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

		callTossCancelApi(payment.getPaymentKey(), cancelRequest.getCancelReason(), encodedSecretKey)
			.doOnSuccess(tossResponse -> {
				payment.cancel();
				payment.getBooking().cancel();
				paymentRepository.save(payment); // 💡 [추가] 명시적 save
				bookingRepository.save(payment.getBooking()); // 💡 [추가] 명시적 save

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

				PaymentCancelHistory history = PaymentCancelHistory.builder()
					.payment(payment)
					.transactionKey(transactionKey)
					.cancelAmount(cancelAmount)
					.cancelReason(cancelRequest.getCancelReason())
					.canceledAt(canceledAt)
					.build();
				paymentCancelHistoryRepository.save(history);

				log.info("결제 취소 완료: orderId={}", orderId);
			})
			.doOnError(e -> {
				log.error("결제 취소 중 오류 발생: orderId={}, 오류={}", orderId, e.getMessage(), e);
				throw new RuntimeException("결제 취소에 실패했습니다. (내부 오류)", e);
			})
			.block();
	}

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

	@Transactional
	public void updatePaymentStatusByWebhook(String orderId, String status) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> {
				log.warn("웹훅 처리: 존재하지 않는 주문 ID 입니다 - {}", orderId);
				return new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId);
			});

		PaymentStatus newStatus = PaymentStatus.valueOf(status.toUpperCase());

		if (payment.getStatus() == newStatus) {
			log.info("웹훅 처리: 주문 ID {}의 상태가 이미 {}입니다. 변경 없음.", orderId, newStatus);
			return;
		}

		if (newStatus == PaymentStatus.DONE) {
			payment.complete(payment.getPaymentKey(),
				LocalDateTime.now()); // 💡 payment.getPaymentKey()와 LocalDateTime.now() 사용
			payment.getBooking().confirm();
		} else if (newStatus == PaymentStatus.CANCELED) {
			payment.cancel();
			payment.getBooking().cancel();
		}

		log.info("주문 ID {} 의 결제 상태가 웹훅을 통해 {} 로 변경되었습니다.", orderId, newStatus);
	}

	@Transactional(readOnly = true)
	public List<PaymentHistoryDto> getPaymentHistoryByUserId(Long userId) {
		return paymentRepository.findByBooking_UserId(userId)
			.stream()
			.map(PaymentHistoryDto::new)
			.collect(Collectors.toList());
	}
}
