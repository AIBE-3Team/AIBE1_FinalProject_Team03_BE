package com.team03.ticketmon.concert.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.concert.dto.ExpectationReviewDTO;
import com.team03.ticketmon.concert.service.ExpectationReviewService;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
	controllers = ExpectationReviewController.class,
	excludeAutoConfiguration = {
		SecurityAutoConfiguration.class,
		UserDetailsServiceAutoConfiguration.class
	}
)
@DisplayName("ExpectationReviewController 테스트")
class ExpectationReviewControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private ExpectationReviewService expectationReviewService;

	@Autowired
	private ObjectMapper objectMapper;

	private ExpectationReviewDTO testExpectationDTO;
	private final Long TEST_CONCERT_ID = 1L;
	private final Long TEST_EXPECTATION_ID = 1L;

	@BeforeEach
	void setUp() {
		testExpectationDTO = new ExpectationReviewDTO(
			1L,
			TEST_CONCERT_ID,
			1L,
			"테스트유저",
			"정말 기대되는 콘서트입니다!",
			5,
			LocalDateTime.now(),
			LocalDateTime.now()
		);
	}

	// ========== 기대평 목록 조회 테스트 ==========
	@Nested
	@DisplayName("기대평 목록 조회")
	class GetExpectationReviewsTest {

		@Test
		@Tag("api")
		@DisplayName("성공 - 기본 페이징 파라미터로 조회")
		void getExpectationReviews_Success_DefaultPaging() throws Exception {
			// === Given ===
			List<ExpectationReviewDTO> reviews = Arrays.asList(testExpectationDTO);
			Page<ExpectationReviewDTO> pageResult = new PageImpl<>(reviews, PageRequest.of(0, 10), 1);

			given(expectationReviewService.getConcertExpectationReviews(eq(TEST_CONCERT_ID), any(Pageable.class)))
				.willReturn(pageResult);

			// === When & Then ===
			mockMvc.perform(get("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content").isArray())
				.andExpect(jsonPath("$.data.content[0].id").value(1))
				.andExpect(jsonPath("$.data.content[0].concertId").value(TEST_CONCERT_ID))
				.andExpect(jsonPath("$.data.content[0].comment").value("정말 기대되는 콘서트입니다!"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.size").value(10))
				.andExpect(jsonPath("$.data.number").value(0));

			// Service 호출 검증
			verify(expectationReviewService).getConcertExpectationReviews(
				eq(TEST_CONCERT_ID),
				argThat(pageable ->
					pageable.getPageNumber() == 0 &&
						pageable.getPageSize() == 10 &&
						pageable.getSort().getOrderFor("createdAt").getDirection().isDescending()
				)
			);
		}

		@Test
		@Tag("api")
		@DisplayName("성공 - 커스텀 페이징 파라미터로 조회")
		void getExpectationReviews_Success_CustomPaging() throws Exception {
			// === Given ===
			Page<ExpectationReviewDTO> emptyPage = new PageImpl<>(Arrays.asList(), PageRequest.of(2, 5), 0);

			given(expectationReviewService.getConcertExpectationReviews(eq(TEST_CONCERT_ID), any(Pageable.class)))
				.willReturn(emptyPage);

			// === When & Then ===
			mockMvc.perform(get("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.param("page", "2")
					.param("size", "5"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isEmpty())
				.andExpect(jsonPath("$.data.number").value(2))
				.andExpect(jsonPath("$.data.size").value(5));
		}

		@Test
		@Tag("api")
		@Tag("edge-case")
		@DisplayName("성공 - 빈 결과 조회")
		void getExpectationReviews_Success_EmptyResult() throws Exception {
			// === Given ===
			Page<ExpectationReviewDTO> emptyPage = new PageImpl<>(Arrays.asList(), PageRequest.of(0, 10), 0);

			given(expectationReviewService.getConcertExpectationReviews(eq(TEST_CONCERT_ID), any(Pageable.class)))
				.willReturn(emptyPage);

			// === When & Then ===
			mockMvc.perform(get("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content").isEmpty())
				.andExpect(jsonPath("$.data.totalElements").value(0));
		}

		@Test
		@Tag("api")
		@Tag("validation")
		@DisplayName("실패 - 잘못된 페이징 파라미터 (음수)")
		void getExpectationReviews_Fail_InvalidPagingParams() throws Exception {
			// === When & Then ===
			mockMvc.perform(get("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.param("page", "-1")
					.param("size", "0"))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}
	}

	// ========== 기대평 작성 테스트 ==========
	@Nested
	@DisplayName("기대평 작성")
	class CreateExpectationReviewTest {

		@Test
		@Tag("api")
		@Tag("business-logic")
		@DisplayName("성공 - 정상적인 기대평 작성")
		void createExpectationReview_Success() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();

			given(expectationReviewService.createExpectationReview(any(ExpectationReviewDTO.class)))
				.willReturn(testExpectationDTO);

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("기대평이 작성되었습니다."))
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.data.concertId").value(TEST_CONCERT_ID))
				.andExpect(jsonPath("$.data.comment").value("정말 기대되는 콘서트입니다!"));

			// concertId가 자동으로 설정되었는지 검증
			verify(expectationReviewService).createExpectationReview(argThat(dto ->
				dto.getConcertId().equals(TEST_CONCERT_ID)
			));
		}

		@Test
		@Tag("api")
		@Tag("business-logic")
		@DisplayName("성공 - concertId 필드 없이 요청 (실제 프론트엔드 시나리오)")
		void createExpectationReview_Success_WithoutConcertIdField() throws Exception {
			// === Given ===
			String requestJsonWithoutConcertId = """
            {
                "userId": 1,
                "userNickname": "테스트유저",
                "comment": "정말 기대되는 콘서트입니다!",
                "expectationRating": 5
            }
            """;

			given(expectationReviewService.createExpectationReview(any(ExpectationReviewDTO.class)))
				.willReturn(testExpectationDTO);

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestJsonWithoutConcertId))
				.andDo(print())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true));

			// Controller에서 concertId가 자동으로 설정되었는지 검증
			verify(expectationReviewService).createExpectationReview(argThat(dto ->
				dto.getConcertId() != null && dto.getConcertId().equals(TEST_CONCERT_ID)
			));
		}

		@Test
		@Tag("api")
		@Tag("validation")
		@DisplayName("실패 - 필수 필드 누락 (userId)")
		void createExpectationReview_Fail_MissingUserId() throws Exception {
			// === Given ===
			String invalidRequestJson = """
            {
                "userNickname": "테스트유저",
                "comment": "정말 기대되는 콘서트입니다!",
                "expectationRating": 5
            }
            """;

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(invalidRequestJson))
				.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));

			// Service가 호출되지 않았는지 검증
			verify(expectationReviewService, never()).createExpectationReview(any());
		}

		@Test
		@Tag("api")
		@Tag("validation")
		@DisplayName("실패 - 빈 닉네임")
		void createExpectationReview_Fail_BlankNickname() throws Exception {
			// === Given ===
			String invalidRequestJson = """
            {
                "userId": 1,
                "userNickname": "",
                "comment": "정말 기대되는 콘서트입니다!",
                "expectationRating": 5
            }
            """;

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(invalidRequestJson))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(expectationReviewService, never()).createExpectationReview(any());
		}

		@Test
		@Tag("api")
		@Tag("validation")
		@DisplayName("실패 - 빈 기대평 내용")
		void createExpectationReview_Fail_BlankComment() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();
			requestDTO.setComment("   "); // 공백만 있는 내용

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(expectationReviewService, never()).createExpectationReview(any());
		}

		@Test
		@Tag("api")
		@Tag("validation")
		@DisplayName("실패 - 기대 점수 범위 초과 (6점)")
		void createExpectationReview_Fail_RatingTooHigh() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();
			requestDTO.setExpectationRating(6); // 1-5 범위 초과

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(expectationReviewService, never()).createExpectationReview(any());
		}

		@Test
		@Tag("api")
		@Tag("validation")
		@DisplayName("실패 - 기대 점수 범위 미만 (0점)")
		void createExpectationReview_Fail_RatingTooLow() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();
			requestDTO.setExpectationRating(0); // 1-5 범위 미만

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(expectationReviewService, never()).createExpectationReview(any());
		}

		@Test
		@Tag("api")
		@Tag("validation")
		@DisplayName("실패 - 닉네임 길이 초과 (51자)")
		void createExpectationReview_Fail_NicknameTooLong() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();
			requestDTO.setUserNickname("a".repeat(51)); // 50자 초과

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(expectationReviewService, never()).createExpectationReview(any());
		}

		@Test
		@Tag("api")
		@Tag("validation")
		@DisplayName("실패 - 기대평 내용 길이 초과 (501자)")
		void createExpectationReview_Fail_CommentTooLong() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();
			requestDTO.setComment("a".repeat(501)); // 500자 초과

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(expectationReviewService, never()).createExpectationReview(any());
		}

		@Test
		@Tag("api")
		@Tag("business-logic")
		@DisplayName("실패 - 존재하지 않는 콘서트")
		void createExpectationReview_Fail_ConcertNotFound() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();

			given(expectationReviewService.createExpectationReview(any(ExpectationReviewDTO.class)))
				.willThrow(new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isNotFound());
		}

		@Test
		@Tag("api")
		@Tag("edge-case")
		@DisplayName("실패 - 잘못된 JSON 형식")
		void createExpectationReview_Fail_InvalidJson() throws Exception {
			// === Given ===
			String invalidJson = "{ invalid json }";

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(invalidJson))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(expectationReviewService, never()).createExpectationReview(any());
		}

		@Test
		@Tag("api")
		@Tag("integration")
		@DisplayName("실패 - 빈 JSON 객체 전송")
		void createExpectationReview_Fail_EmptyJsonObject() throws Exception {
			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}")) // 빈 JSON 객체
				.andDo(print())
				.andExpect(status().isBadRequest()); // 필수 필드 누락으로 400

			verify(expectationReviewService, never()).createExpectationReview(any());
		}

		@Test
		@Tag("api")
		@Tag("integration")
		@DisplayName("실패 - null 값들로 구성된 JSON")
		void createExpectationReview_Fail_NullValuesJson() throws Exception {
			// === Given ===
			String nullValuesJson = """
            {
                "userId": null,
                "userNickname": null,
                "comment": null,
                "expectationRating": null
            }
            """;

			// === When & Then ===
			mockMvc.perform(post("/api/concerts/{concertId}/expectations", TEST_CONCERT_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(nullValuesJson))
				.andDo(print())
				.andExpect(status().isBadRequest()); // 유효성 검증 실패

			verify(expectationReviewService, never()).createExpectationReview(any());
		}
	}

	// ========== 기대평 수정 테스트 ==========
	@Nested
	@DisplayName("기대평 수정")
	class UpdateExpectationReviewTest {

		@Test
		@Tag("api")
		@Tag("business-logic")
		@DisplayName("성공 - 기대평 수정")
		void updateExpectationReview_Success() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();
			requestDTO.setComment("수정된 기대평 내용입니다");
			requestDTO.setExpectationRating(4);

			ExpectationReviewDTO updatedDTO = new ExpectationReviewDTO(
				TEST_EXPECTATION_ID, TEST_CONCERT_ID, 1L, "테스트유저",
				"수정된 기대평 내용입니다", 4, LocalDateTime.now(), LocalDateTime.now()
			);

			given(expectationReviewService.updateExpectationReview(
				eq(TEST_CONCERT_ID), eq(TEST_EXPECTATION_ID), any(ExpectationReviewDTO.class)))
				.willReturn(Optional.of(updatedDTO));

			// === When & Then ===
			mockMvc.perform(put("/api/concerts/{concertId}/expectations/{expectationId}",
					TEST_CONCERT_ID, TEST_EXPECTATION_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("기대평이 수정되었습니다."))
				.andExpect(jsonPath("$.data.comment").value("수정된 기대평 내용입니다"))
				.andExpect(jsonPath("$.data.expectationRating").value(4));
		}

		@Test
		@Tag("api")
		@Tag("business-logic")
		@DisplayName("실패 - 존재하지 않는 기대평 수정")
		void updateExpectationReview_Fail_NotFound() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();

			given(expectationReviewService.updateExpectationReview(
				eq(TEST_CONCERT_ID), eq(999L), any(ExpectationReviewDTO.class)))
				.willReturn(Optional.empty());

			// === When & Then ===
			mockMvc.perform(put("/api/concerts/{concertId}/expectations/{expectationId}",
					TEST_CONCERT_ID, 999L)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("기대평을 찾을 수 없습니다."));
		}

		@Test
		@Tag("api")
		@Tag("validation")
		@DisplayName("실패 - 수정 시 유효성 검증 실패")
		void updateExpectationReview_Fail_ValidationError() throws Exception {
			// === Given ===
			ExpectationReviewDTO requestDTO = createTestRequestDTO();
			requestDTO.setComment(""); // 빈 내용
			requestDTO.setExpectationRating(null); // null 점수

			// === When & Then ===
			mockMvc.perform(put("/api/concerts/{concertId}/expectations/{expectationId}",
					TEST_CONCERT_ID, TEST_EXPECTATION_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(expectationReviewService, never()).updateExpectationReview(anyLong(), anyLong(), any());
		}
	}

	// ========== 기대평 삭제 테스트 ==========
	@Nested
	@DisplayName("기대평 삭제")
	class DeleteExpectationReviewTest {

		@Test
		@Tag("api")
		@Tag("business-logic")
		@DisplayName("성공 - 기대평 삭제")
		void deleteExpectationReview_Success() throws Exception {
			// === Given ===
			given(expectationReviewService.deleteExpectationReview(TEST_CONCERT_ID, TEST_EXPECTATION_ID))
				.willReturn(true);

			// === When & Then ===
			mockMvc.perform(delete("/api/concerts/{concertId}/expectations/{expectationId}",
					TEST_CONCERT_ID, TEST_EXPECTATION_ID))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("기대평이 삭제되었습니다."));
		}

		@Test
		@Tag("api")
		@Tag("business-logic")
		@DisplayName("실패 - 존재하지 않는 기대평 삭제")
		void deleteExpectationReview_Fail_NotFound() throws Exception {
			// === Given ===
			given(expectationReviewService.deleteExpectationReview(TEST_CONCERT_ID, 999L))
				.willReturn(false);

			// === When & Then ===
			mockMvc.perform(delete("/api/concerts/{concertId}/expectations/{expectationId}",
					TEST_CONCERT_ID, 999L))
				.andDo(print())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("기대평을 찾을 수 없습니다."));
		}
	}

	// ========== 헬퍼 메서드 ==========
	private ExpectationReviewDTO createTestRequestDTO() {
		ExpectationReviewDTO dto = new ExpectationReviewDTO();
		dto.setUserId(1L);
		dto.setUserNickname("테스트유저");
		dto.setComment("정말 기대되는 콘서트입니다!");
		dto.setExpectationRating(5);
		return dto;
	}
}