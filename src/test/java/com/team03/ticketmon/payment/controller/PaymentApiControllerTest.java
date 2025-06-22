package com.team03.ticketmon.payment.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentRequest;
import com.team03.ticketmon.payment.service.PaymentService;

@WebMvcTest(PaymentApiController.class)
class PaymentApiControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private PaymentService paymentService;

	@Test
	@DisplayName("[성공] 결제 요청 성공")
	@WithMockUser
	void initiatePaymentSuccess() throws Exception {
		// GIVEN
		String bookingNumber = "B-TEST-12345";
		PaymentRequest paymentRequest = new PaymentRequest();
		ReflectionTestUtils.setField(paymentRequest, "bookingNumber", bookingNumber);

		PaymentExecutionResponse fakeResponse = PaymentExecutionResponse.builder()
			.orderId(UUID.randomUUID().toString())
			.orderName("테스트 콘서트")
			.amount(new BigDecimal("55000"))
			.build();

		when(paymentService.initiatePayment(any(PaymentRequest.class))).thenReturn(fakeResponse);

		// WHEN & THEN
		mockMvc.perform(post("/api/v1/payments/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(paymentRequest))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId").exists())
			.andExpect(jsonPath("$.orderName").value("테스트 콘서트"))
			.andExpect(jsonPath("$.amount").value(55000))
			.andDo(print());
	}

	// =================================================================
	// 💡 [포트폴리오 업그레이드] 실패 케이스 테스트 추가
	// =================================================================
	@Test
	@DisplayName("[실패] 결제 요청 실패 - bookingNumber가 비어있음")
	@WithMockUser
	void initiatePaymentFail_whenBookingNumberIsBlank() throws Exception {
		// GIVEN
		PaymentRequest paymentRequest = new PaymentRequest();
		ReflectionTestUtils.setField(paymentRequest, "bookingNumber", " "); // 💡 공백 문자를 보내 유효성 검증 실패 유도

		// WHEN & THEN
		mockMvc.perform(post("/api/v1/payments/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(paymentRequest))
				.with(csrf()))
			.andExpect(status().isBadRequest()) // 💡 HTTP 400 Bad Request를 기대합니다.
			.andDo(print());
	}

	@Test
	@DisplayName("[성공] 결제 취소 성공")
	@WithMockUser
	void cancelPaymentSuccess() throws Exception {
		// GIVEN
		String orderId = "test-order-id-for-cancel";
		PaymentCancelRequest cancelRequest = new PaymentCancelRequest();
		ReflectionTestUtils.setField(cancelRequest, "cancelReason", "테스트 취소");

		doNothing().when(paymentService).cancelPayment(anyString(), any(PaymentCancelRequest.class));

		// WHEN & THEN
		mockMvc.perform(post("/api/v1/payments/{orderId}/cancel", orderId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(cancelRequest))
				.with(csrf()))
			.andExpect(status().isOk())
			.andDo(print());
	}
}
