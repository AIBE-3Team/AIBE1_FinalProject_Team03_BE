package com.team03.ticketmon.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.booking.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentRequest;
import com.team03.ticketmon.payment.repository.PaymentCancelHistoryRepository;
import com.team03.ticketmon.payment.repository.PaymentRepository;

import reactor.core.publisher.Mono;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@Mock
	private BookingRepository bookingRepository;
	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private PaymentCancelHistoryRepository paymentCancelHistoryRepository;
	@Mock
	private TossPaymentsProperties tossPaymentsProperties;
	@Mock
	private AppProperties appProperties;
	@Mock
	private WebClient webClient;
	@Mock
	private WebClient.RequestBodyUriSpec requestBodyUriSpec;
	@Mock
	private WebClient.RequestBodySpec requestBodySpec;
	@Mock
	private WebClient.ResponseSpec responseSpec;
	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpec;

	@InjectMocks
	private PaymentService paymentService;

	private Booking testBooking;
	private PaymentConfirmRequest confirmRequest;
	private PaymentRequest paymentRequest;
	private PaymentCancelRequest cancelRequest;

	@BeforeEach
	void setUp() {
		Concert mockConcert = Concert.builder().title("테스트 콘서트").build();
		testBooking = Booking.builder()
			.bookingNumber("B12345")
			.totalAmount(new BigDecimal("10000"))
			.status(BookingStatus.PENDING_PAYMENT)
			.concert(mockConcert)
			.userId(1L)
			.build();

		confirmRequest = PaymentConfirmRequest.builder()
			.paymentKey("pk_test_123")
			.orderId("order_test_1234")
			.amount(new BigDecimal("10000"))
			.build();
		paymentRequest = PaymentRequest.builder().bookingNumber("B12345").build();
		cancelRequest = PaymentCancelRequest.builder().cancelReason("고객 변심").build();

		when(webClient.post()).thenReturn(requestBodyUriSpec);
		when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
		when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
		when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
		when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
		when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

		when(tossPaymentsProperties.secretKey()).thenReturn("test_secret_key");
		when(tossPaymentsProperties.clientKey()).thenReturn("test_client_key");
		when(appProperties.baseUrl()).thenReturn("http://localhost:8080");
	}

	// --- initiatePayment 테스트 (이전과 동일) ---
	@Test
	@DisplayName("initiatePayment는 유효한 요청 시 결제 정보를 성공적으로 생성해야 한다")
	void initiatePayment_shouldCreatePaymentSuccessfully() {
		Payment testPayment = Payment.builder()
			.booking(testBooking)
			.orderId("order_test_initiate")
			.amount(new BigDecimal("10000"))
			.build();
		when(bookingRepository.findByBookingNumber(anyString())).thenReturn(Optional.of(testBooking));
		when(paymentRepository.findByBooking(any(Booking.class))).thenReturn(Optional.empty());
		when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

		PaymentExecutionResponse response = paymentService.initiatePayment(paymentRequest);

		assertThat(response).isNotNull();
		assertThat(response.getOrderId()).isEqualTo(testPayment.getOrderId());
		assertThat(response.getAmount()).isEqualTo(testBooking.getTotalAmount());
		verify(paymentRepository, times(1)).save(any(Payment.class));
	}

	@Test
	@DisplayName("initiatePayment는 존재하지 않는 예매 번호 시 IllegalArgumentException을 발생시켜야 한다")
	void initiatePayment_shouldThrowExceptionForNonExistentBookingNumber() {
		when(bookingRepository.findByBookingNumber(anyString())).thenReturn(Optional.empty());
		assertThatThrownBy(() -> paymentService.initiatePayment(paymentRequest))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("존재하지 않는 예매 번호입니다.");
	}

	@Test
	@DisplayName("initiatePayment는 결제를 진행할 수 없는 예매 상태 시 IllegalStateException을 발생시켜야 한다")
	void initiatePayment_shouldThrowExceptionForInvalidBookingStatus() {
		testBooking.confirm();
		when(bookingRepository.findByBookingNumber(anyString())).thenReturn(Optional.of(testBooking));
		assertThatThrownBy(() -> paymentService.initiatePayment(paymentRequest))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("결제를 진행할 수 없는 예매 상태입니다.");
	}

	// --- confirmPayment 테스트 ---

	@Test
	@DisplayName("confirmPayment 호출 시 Toss API에 Idempotency-Key 헤더가 포함되어야 한다")
	void confirmPayment_shouldIncludeIdempotencyKeyHeader() {
		Payment testPayment = Payment.builder()
			.booking(testBooking)
			.orderId("order_test_1234")
			.amount(new BigDecimal("10000"))
			.build();
		when(paymentRepository.findByOrderId(anyString())).thenReturn(Optional.of(testPayment));
		Map<String, Object> tossResponse = Map.of(
			"paymentKey", "pk_test_123",
			"approvedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
		);
		when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(tossResponse));

		paymentService.confirmPayment(confirmRequest);

		ArgumentCaptor<String> headerNameCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);

		verify(requestBodySpec, times(2)).header(headerNameCaptor.capture(), headerValueCaptor.capture());

		assertThat(headerNameCaptor.getAllValues()).contains("Idempotency-Key");
		assertThat(headerValueCaptor.getAllValues()).contains(confirmRequest.getOrderId());
	}

	@Test
	@DisplayName("confirmPayment는 유효한 요청 시 결제를 성공적으로 처리하고 DB를 업데이트해야 한다")
	void confirmPayment_shouldProcessPaymentSuccessfullyAndUpdateDb() {
		Payment testPayment = Payment.builder()
			.booking(testBooking)
			.orderId("order_test_1234")
			.amount(new BigDecimal("10000"))
			.build();
		when(paymentRepository.findByOrderId(anyString())).thenReturn(Optional.of(testPayment));
		Map<String, Object> tossResponse = Map.of(
			"paymentKey", "pk_test_123",
			"approvedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
		);
		when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(tossResponse));

		paymentService.confirmPayment(confirmRequest);

		assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.DONE);
		assertThat(testBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
		verify(paymentRepository, times(1)).save(testPayment);
		verify(bookingRepository, times(1)).save(testBooking);
	}

	@Test
	@DisplayName("confirmPayment는 금액 불일치 시 IllegalArgumentException을 발생시켜야 한다")
	void confirmPayment_shouldThrowExceptionOnAmountMismatch() {
		Payment testPayment = Payment.builder()
			.booking(testBooking)
			.orderId("order_test_1234")
			.amount(new BigDecimal("10000"))
			.build();
		PaymentConfirmRequest mismatchRequest = PaymentConfirmRequest.builder()
			.paymentKey("pk_test_123")
			.orderId("order_test_1234")
			.amount(new BigDecimal("5000"))
			.build();
		when(paymentRepository.findByOrderId(anyString())).thenReturn(Optional.of(testPayment));

		assertThatThrownBy(() -> paymentService.confirmPayment(mismatchRequest))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("결제 금액이 일치하지 않습니다.");
		verify(webClient, never()).post();
	}

	@Test
	@DisplayName("confirmPayment는 이미 처리된 결제 요청 시 추가 처리 없이 종료해야 한다")
	void confirmPayment_shouldIgnoreAlreadyProcessedRequest() {
		Payment testPayment = Payment.builder()
			.booking(testBooking)
			.orderId("order_test_1234")
			.amount(new BigDecimal("10000"))
			.build();
		testPayment.complete("pk_already_done", LocalDateTime.now());
		when(paymentRepository.findByOrderId(anyString())).thenReturn(Optional.of(testPayment));

		paymentService.confirmPayment(confirmRequest);

		assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.DONE);
		verify(webClient, never()).post();
	}

	// --- cancelPayment 테스트 ---

	@Test
	@DisplayName("cancelPayment는 유효한 요청 시 결제를 성공적으로 취소해야 한다")
	void cancelPayment_shouldCancelPaymentSuccessfully() {
		Payment testPayment = Payment.builder()
			.booking(testBooking)
			.orderId("order_test_1234")
			.amount(new BigDecimal("10000"))
			.build();
		testPayment.complete("pk_test_123", LocalDateTime.now()); // 취소하려면 DONE 상태여야 함

		when(paymentRepository.findByOrderId(anyString())).thenReturn(Optional.of(testPayment));
		Map<String, Object> tossResponse = Map.of("status", "CANCELED", "cancels", List.of(
			Map.of("transactionKey", "tk_cancel", "cancelAmount", 10000, "canceledAt",
				OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))));
		when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(tossResponse));

		paymentService.cancelPayment(testPayment.getOrderId(), cancelRequest);

		assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(testBooking.getStatus()).isEqualTo(BookingStatus.CANCELED);
		verify(paymentRepository, times(1)).save(testPayment);
		verify(bookingRepository, times(1)).save(testBooking);
		verify(paymentCancelHistoryRepository, times(1)).save(any());
	}

	@Test
	@DisplayName("cancelPayment는 이미 취소된 결제 시 IllegalStateException을 발생시켜야 한다")
	void cancelPayment_shouldThrowExceptionIfAlreadyCancelled() {
		Payment testPayment = Payment.builder()
			.booking(testBooking)
			.orderId("order_test_1234")
			.amount(new BigDecimal("10000"))
			.build();
		testPayment.cancel(); // CANCELED 상태로 만듦
		when(paymentRepository.findByOrderId(anyString())).thenReturn(Optional.of(testPayment));

		assertThatThrownBy(() -> paymentService.cancelPayment(testPayment.getOrderId(), cancelRequest))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("이미 취소된 결제입니다.");
		verify(webClient, never()).post();
	}

	@Test
	@DisplayName("cancelPayment는 DONE 상태가 아닌 결제 시 IllegalStateException을 발생시켜야 한다")
	void cancelPayment_shouldThrowExceptionIfNotDone() {
		Payment testPayment = Payment.builder()
			.booking(testBooking)
			.orderId("order_test_1234")
			.amount(new BigDecimal("10000"))
			.build();
		// 기본 상태 PENDING
		when(paymentRepository.findByOrderId(anyString())).thenReturn(Optional.of(testPayment));

		assertThatThrownBy(() -> paymentService.cancelPayment(testPayment.getOrderId(), cancelRequest))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("결제 완료 상태에서만 취소가 가능합니다.");
		verify(webClient, never()).post();
	}

	// --- handlePaymentFailure 테스트 ---
	@Test
	@DisplayName("handlePaymentFailure는 결제 실패를 정확히 반영해야 한다")
	void handlePaymentFailure_shouldReflectFailureCorrectly() {
		Payment testPayment = Payment.builder()
			.booking(testBooking)
			.orderId("order_test_1234")
			.amount(new BigDecimal("10000"))
			.build();
		when(paymentRepository.findByOrderId(anyString())).thenReturn(Optional.of(testPayment));

		paymentService.handlePaymentFailure(testPayment.getOrderId(), "FAIL_CODE", "FAIL_MESSAGE");

		assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(testBooking.getStatus()).isEqualTo(BookingStatus.CANCELED);
		verify(paymentRepository, times(1)).save(testPayment);
		verify(bookingRepository, times(1)).save(testBooking);
	}
}
