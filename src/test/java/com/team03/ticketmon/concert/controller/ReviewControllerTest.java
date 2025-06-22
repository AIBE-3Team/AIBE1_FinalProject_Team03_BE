package com.team03.ticketmon.concert.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.service.ReviewService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Security 및 JPA 자동 설정 제외
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

@WebMvcTest(controllers = ReviewController.class,
	excludeAutoConfiguration = {
		HibernateJpaAutoConfiguration.class,
		JpaRepositoriesAutoConfiguration.class,
		DataSourceAutoConfiguration.class,
		DataSourceTransactionManagerAutoConfiguration.class,
		SecurityAutoConfiguration.class,
		SecurityFilterAutoConfiguration.class
	})
@ActiveProfiles("test")
@Tag("concert")
@Tag("controller")
@Tag("review")
@DisplayName("후기 컨트롤러 테스트 - 실제 동작 검증")
class ReviewControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private ReviewService reviewService;

	private ReviewDTO testReviewDTO;

	@BeforeEach
	void setUp() {
		testReviewDTO = createTestReviewDTO();
	}

	// ========== 실제 컨트롤러 동작 검증 테스트들 ==========

	@Test
	@Tag("api")
	@DisplayName("후기 작성 성공 - 정상적인 요청")
	void createReview_Success() throws Exception {
		// === Given: 테스트 조건 설정 ===
		Long concertId = 1L;
		ReviewDTO requestDTO = createTestReviewRequestDTO();
		// ReviewService가 정상적으로 ReviewDTO를 반환하도록 Mock 설정
		given(reviewService.createReview(any(ReviewDTO.class))).willReturn(testReviewDTO);

		// === When & Then: 실제 HTTP 요청 및 응답 검증 ===
		mockMvc.perform(post("/api/concerts/{concertId}/reviews", concertId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDTO)))
			.andDo(print()) // 실제 요청/응답 로그 출력
			.andExpect(status().isOk()) // HTTP 200 상태 확인
			.andExpect(jsonPath("$.success").value(true)) // SuccessResponse 구조 확인
			.andExpect(jsonPath("$.message").value("후기가 작성되었습니다.")) // 메시지 확인
			.andExpect(jsonPath("$.data").exists()) // 응답 데이터 존재 확인
			.andExpect(jsonPath("$.data.title").value(testReviewDTO.getTitle())); // 제목 확인

		// === Then: 서비스 메서드 호출 검증 ===
		verify(reviewService).createReview(any(ReviewDTO.class));
	}

	// ========== concertId 필드 없이 요청 테스트 ==========
	@Test
	@Tag("api")
	@Tag("business-logic")
	@DisplayName("후기 작성 성공 - concertId 필드 없이 요청")
	void createReview_WithoutConcertIdField() throws Exception {
		// === Given ===
		Long concertId = 1L;

		// concertId 필드가 아예 없는 JSON 요청
		String requestJsonWithoutConcertId = """
    {
        "userId": 1,
        "userNickname": "테스트유저",
        "title": "정말 좋은 콘서트",
        "description": "감동적인 공연이었습니다",
        "rating": 5
    }
    """;

		given(reviewService.createReview(any(ReviewDTO.class))).willReturn(testReviewDTO);

		// === When & Then ===
		mockMvc.perform(post("/api/concerts/{concertId}/reviews", concertId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJsonWithoutConcertId))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		// Controller에서 concertId가 자동으로 설정되었는지 검증
		verify(reviewService).createReview(argThat(dto ->
			dto.getConcertId() != null && dto.getConcertId().equals(concertId)
		));
	}

	// ========== concertId 일치하는 경우 덮어쓰기 테스트 ==========
	@Test
	@Tag("api")
	@Tag("business-logic")
	@DisplayName("후기 작성 성공 - concertId 일치시에도 덮어쓰기 로직 실행")
	void createReview_ConcertIdOverrideSameValue() throws Exception {
		// === Given ===
		Long concertId = 1L;
		ReviewDTO requestDTO = createTestReviewRequestDTO();
		requestDTO.setConcertId(concertId); // URL과 같은 값으로 설정

		given(reviewService.createReview(any(ReviewDTO.class))).willReturn(testReviewDTO);

		// === When & Then ===
		mockMvc.perform(post("/api/concerts/{concertId}/reviews", concertId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDTO)))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		// 덮어쓰기 로직이 실행되어 같은 값이 유지되는지 검증
		verify(reviewService).createReview(argThat(dto ->
			dto.getConcertId().equals(concertId)
		));
	}

	// ========== concertId 불일치 에러 테스트 ==========
	@Test
	@Tag("api")
	@Tag("error-handling")
	@DisplayName("후기 작성 실패 - concertId 불일치시 400 에러")
	void createReview_ConcertIdMismatchError() throws Exception {
		// === Given ===
		Long urlConcertId = 1L;
		Long bodyConcertId = 2L; // 다른 값

		ReviewDTO requestDTO = createTestReviewRequestDTO();
		requestDTO.setConcertId(bodyConcertId); // URL과 다른 concertId

		// === When & Then ===
		mockMvc.perform(post("/api/concerts/{concertId}/reviews", urlConcertId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDTO)))
			.andDo(print())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false));

		// 에러 발생으로 Service가 호출되지 않았는지 검증
		verify(reviewService, never()).createReview(any());
	}

	@Test
	@Tag("api")
	@Tag("business-logic")
	@DisplayName("후기 작성 실패 - concertId 불일치시 BusinessException 발생")
	void createReview_ConcertIdMismatch() throws Exception {
		// === Given ===
		Long urlConcertId = 1L;
		ReviewDTO requestDTO = createTestReviewRequestDTO();
		requestDTO.setConcertId(2L); // URL과 다른 concertId로 설정

		// === When & Then ===
		// 컨트롤러에서 BusinessException이 발생해야 함
		mockMvc.perform(post("/api/concerts/{concertId}/reviews", urlConcertId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDTO)))
			.andDo(print())
			.andExpect(status().isBadRequest()); // BusinessException -> 400 상태

		// === Then: 서비스가 호출되지 않아야 함 ===
		verify(reviewService, never()).createReview(any());
	}

	@Test
	@Tag("api")
	@Tag("validation")
	@DisplayName("후기 작성 실패 - @Valid 검증 오류 (필수 필드 누락)")
	void createReview_ValidationError_MissingFields() throws Exception {
		// === Given: 필수 필드가 누락된 잘못된 DTO ===
		Long concertId = 1L;
		ReviewDTO invalidDTO = new ReviewDTO();
		invalidDTO.setConcertId(concertId);
		// title, description, rating 등 @NotBlank, @NotNull 필드들이 누락됨

		// === When & Then ===
		mockMvc.perform(post("/api/concerts/{concertId}/reviews", concertId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidDTO)))
			.andDo(print())
			.andExpect(status().isBadRequest()); // @Valid 검증 실패 -> 400

		// === Then: 서비스가 호출되지 않아야 함 ===
		verify(reviewService, never()).createReview(any());
	}

	@Test
	@Tag("api")
	@Tag("validation")
	@DisplayName("후기 작성 실패 - @Valid 검증 오류 (빈 문자열)")
	void createReview_ValidationError_BlankFields() throws Exception {
		// === Given: 빈 문자열로 채워진 잘못된 DTO ===
		Long concertId = 1L;
		ReviewDTO invalidDTO = createInvalidReviewDTO(); // 빈 문자열들

		// === When & Then ===
		mockMvc.perform(post("/api/concerts/{concertId}/reviews", concertId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidDTO)))
			.andDo(print())
			.andExpect(status().isBadRequest()); // @Valid 검증 실패

		verify(reviewService, never()).createReview(any());
	}

	@Test
	@Tag("api")
	@Tag("validation")
	@DisplayName("후기 작성 실패 - JSON 파싱 오류")
	void createReview_InvalidJsonFormat() throws Exception {
		// === Given: 잘못된 JSON ===
		Long concertId = 1L;
		String invalidJson = "{ invalid json format }";

		// === When & Then ===
		mockMvc.perform(post("/api/concerts/{concertId}/reviews", concertId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidJson))
			.andDo(print())
			.andExpect(status().isBadRequest()); // JSON 파싱 오류

		verify(reviewService, never()).createReview(any());
	}

	@Test
	@Tag("api")
	@DisplayName("후기 수정 성공")
	void updateReview_Success() throws Exception {
		// === Given ===
		Long concertId = 1L;
		Long reviewId = 1L;
		ReviewDTO updateDTO = createTestReviewUpdateDTO(reviewId, concertId);
		// 서비스에서 수정된 후기를 반환
		given(reviewService.updateReview(reviewId, concertId, updateDTO))
			.willReturn(Optional.of(testReviewDTO));

		// === When & Then ===
		mockMvc.perform(put("/api/concerts/{concertId}/reviews/{reviewId}", concertId, reviewId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(updateDTO)))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("후기가 수정되었습니다."))
			.andExpect(jsonPath("$.data.title").value(testReviewDTO.getTitle()));

		verify(reviewService).updateReview(reviewId, concertId, updateDTO);
	}

	@Test
	@Tag("api")
	@DisplayName("후기 수정 실패 - 존재하지 않는 후기 (404)")
	void updateReview_NotFound() throws Exception {
		// === Given ===
		Long concertId = 1L;
		Long reviewId = 999L; // 존재하지 않는 ID
		ReviewDTO updateDTO = createTestReviewUpdateDTO(reviewId, concertId);
		// 서비스에서 Optional.empty() 반환
		given(reviewService.updateReview(reviewId, concertId, updateDTO))
			.willReturn(Optional.empty());

		// === When & Then ===
		mockMvc.perform(put("/api/concerts/{concertId}/reviews/{reviewId}", concertId, reviewId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(updateDTO)))
			.andDo(print())
			.andExpect(status().isNotFound()) // 404 상태
			.andExpect(content().string("")); // 빈 응답 본문 (ResponseEntity.notFound().build())

		verify(reviewService).updateReview(reviewId, concertId, updateDTO);
	}

	@Test
	@Tag("api")
	@DisplayName("후기 삭제 성공")
	void deleteReview_Success() throws Exception {
		// === Given ===
		Long concertId = 1L;
		Long reviewId = 1L;
		// 서비스에서 삭제 성공을 나타내는 true 반환
		given(reviewService.deleteReview(reviewId, concertId)).willReturn(true);

		// === When & Then ===
		mockMvc.perform(delete("/api/concerts/{concertId}/reviews/{reviewId}", concertId, reviewId))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("후기가 삭제되었습니다."))
			.andExpect(jsonPath("$.data").doesNotExist()); // null 값

		verify(reviewService).deleteReview(reviewId, concertId);
	}

	@Test
	@Tag("api")
	@DisplayName("후기 삭제 실패 - 존재하지 않는 후기 (404)")
	void deleteReview_NotFound() throws Exception {
		// === Given ===
		Long concertId = 1L;
		Long reviewId = 999L; // 존재하지 않는 ID
		// 서비스에서 삭제 실패를 나타내는 false 반환
		given(reviewService.deleteReview(reviewId, concertId)).willReturn(false);

		// === When & Then ===
		mockMvc.perform(delete("/api/concerts/{concertId}/reviews/{reviewId}", concertId, reviewId))
			.andDo(print())
			.andExpect(status().isNotFound()) // 404 상태
			.andExpect(content().string("")); // 빈 응답 본문

		verify(reviewService).deleteReview(reviewId, concertId);
	}

	// ========== 🚫 실제 동작과 무관한 테스트들 제거 ==========
	// 다음 테스트들은 실제 컨트롤러 동작과 무관하므로 제거:
	// - 대용량 콘텐츠 처리 (비즈니스 로직이 아님)
	// - 제목/내용/닉네임 길이 검증 (@Valid는 프레임워크가 처리)
	// - 평점 범위 검증 (@Min, @Max는 프레임워크가 처리)
	// 이런 검증들은 Integration Test에서 처리해야 함

	@Test
	@Tag("api")
	@Tag("edge-case")
	@DisplayName("후기 수정 - 동일한 concertId와 reviewId (엣지 케이스)")
	void updateReview_SameIds() throws Exception {
		// === Given ===
		Long id = 1L; // concertId와 reviewId가 동일한 경우
		ReviewDTO updateDTO = createTestReviewUpdateDTO(id, id);
		given(reviewService.updateReview(id, id, updateDTO))
			.willReturn(Optional.of(testReviewDTO));

		// === When & Then ===
		mockMvc.perform(put("/api/concerts/{concertId}/reviews/{reviewId}", id, id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(updateDTO)))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		verify(reviewService).updateReview(id, id, updateDTO);
	}

	@Test
	@Tag("api")
	@Tag("edge-case")
	@DisplayName("POST 요청에 잘못된 HTTP 메서드 사용")
	void wrongHttpMethod() throws Exception {
		// === Given ===
		Long concertId = 1L;
		ReviewDTO requestDTO = createTestReviewRequestDTO();

		// === When & Then: GET 메서드로 POST 엔드포인트 호출 ===
		mockMvc.perform(get("/api/concerts/{concertId}/reviews", concertId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDTO)))
			.andDo(print())
			.andExpect(status().isMethodNotAllowed()); // 405 Method Not Allowed

		// 서비스가 호출되지 않아야 함
		verify(reviewService, never()).createReview(any());
	}

	// ========== Helper Methods ==========

	private ReviewDTO createTestReviewDTO() {
		ReviewDTO reviewDTO = new ReviewDTO();
		reviewDTO.setId(1L);
		reviewDTO.setConcertId(1L);
		reviewDTO.setUserId(1L);
		reviewDTO.setUserNickname("테스트유저");
		reviewDTO.setTitle("테스트 후기");
		reviewDTO.setDescription("훌륭한 콘서트였습니다");
		reviewDTO.setRating(5);
		reviewDTO.setCreatedAt(LocalDateTime.now());
		reviewDTO.setUpdatedAt(LocalDateTime.now());
		return reviewDTO;
	}

	private ReviewDTO createTestReviewRequestDTO() {
		ReviewDTO reviewDTO = new ReviewDTO();
		reviewDTO.setConcertId(1L);
		reviewDTO.setUserId(1L);
		reviewDTO.setUserNickname("테스트유저");
		reviewDTO.setTitle("새 후기");
		reviewDTO.setDescription("좋은 콘서트였습니다");
		reviewDTO.setRating(5);
		return reviewDTO;
	}

	private ReviewDTO createTestReviewUpdateDTO(Long reviewId, Long concertId) {
		ReviewDTO reviewDTO = new ReviewDTO();
		reviewDTO.setId(reviewId);
		reviewDTO.setConcertId(concertId);
		reviewDTO.setUserId(1L);
		reviewDTO.setUserNickname("테스트유저");
		reviewDTO.setTitle("수정된 후기");
		reviewDTO.setDescription("수정된 내용");
		reviewDTO.setRating(4);
		return reviewDTO;
	}

	private ReviewDTO createInvalidReviewDTO() {
		ReviewDTO reviewDTO = new ReviewDTO();
		reviewDTO.setConcertId(1L);
		reviewDTO.setUserId(1L);
		reviewDTO.setUserNickname(""); // @NotBlank 위반
		reviewDTO.setTitle(""); // @NotBlank 위반
		reviewDTO.setDescription(""); // @NotBlank 위반
		reviewDTO.setRating(0); // @Min(1) 위반
		return reviewDTO;
	}
}