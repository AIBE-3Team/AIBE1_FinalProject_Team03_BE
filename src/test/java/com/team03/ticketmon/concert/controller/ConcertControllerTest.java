package com.team03.ticketmon.concert.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.concert.dto.ConcertDTO;
import com.team03.ticketmon.concert.dto.ConcertSearchDTO;
import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.service.ConcertService;
import com.team03.ticketmon.concert.service.CacheService;
import com.team03.ticketmon.concert.service.ReviewService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// ✨ JPA, DataSource, Security 자동 설정 제외 import 추가
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.data.domain.AuditorAware;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

// 🎯 JPA, DataSource, Security 관련 자동 설정 모두 제외 + JPA Auditing Mock 추가
@WebMvcTest(controllers = ConcertController.class,
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
@DisplayName("콘서트 컨트롤러 테스트")
class ConcertControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private ConcertService concertService;

	@MockBean
	private CacheService cacheService;

	@MockBean
	private ReviewService reviewService;

	// ✨ JPA Auditing을 위한 Mock Bean 추가
	@MockBean
	private AuditorAware<String> auditorProvider;

	private ConcertDTO testConcertDTO;
	private List<ConcertDTO> testConcertList;
	private ReviewDTO testReviewDTO;

	@BeforeEach
	void setUp() {
		testConcertDTO = createTestConcertDTO(1L, "테스트 콘서트", "테스트 아티스트");
		testConcertList = Arrays.asList(
			createTestConcertDTO(1L, "콘서트 1", "아티스트 1"),
			createTestConcertDTO(2L, "콘서트 2", "아티스트 2")
		);
		testReviewDTO = createTestReviewDTO();
	}

	@Test
	@Tag("api")
	@DisplayName("콘서트 목록 조회 성공")
	void getConcerts_Success() throws Exception {
		// === 🎯 Given - 테스트 조건 설정 ===
		// Mock 서비스 동작 정의: getAllConcerts가 호출되면 미리 준비된 페이지 데이터 반환
		Page<ConcertDTO> concertPage = new PageImpl<>(testConcertList);
		given(concertService.getAllConcerts(0, 20)).willReturn(concertPage);

		// === 🚀 When & Then - 실제 테스트 실행 및 검증 ===
		// 기본 매핑 @GetMapping으로 파라미터 없는 요청 테스트
		mockMvc.perform(get("/api/concerts")
				.param("page", "0")     // 📄 페이지 번호
				.param("size", "20"))   // 📄 페이지 크기
			.andDo(print())             // 🔍 실제 요청/응답 콘솔 출력 (디버깅용)
			.andExpect(status().isOk()) // ✅ HTTP 200 상태 코드 확인
			.andExpect(jsonPath("$.success").value(true))           // ✅ 응답 JSON의 success 필드 확인
			.andExpect(jsonPath("$.data.content").isArray())        // ✅ data.content 필드가 배열인지 확인
			.andExpect(jsonPath("$.data.content.length()").value(2)) // ✅ 배열 길이가 2인지 확인
			.andExpect(jsonPath("$.data.content[0].title").value("콘서트 1")); // ✅ 첫 번째 콘서트 제목 확인

		// === 🔍 호출 검증 ===
		// concertService.getAllConcerts가 정확한 파라미터로 한 번 호출되었는지 확인
		verify(concertService).getAllConcerts(0, 20);
	}

	@Test
	@Tag("api")
	@DisplayName("콘서트 목록 조회 - 기본 파라미터")
	void getConcerts_DefaultParameters() throws Exception {
		// === 🎯 Given ===
		// 기본 파라미터(page=0, size=20) 동작 확인
		Page<ConcertDTO> concertPage = new PageImpl<>(testConcertList);
		given(concertService.getAllConcerts(0, 20)).willReturn(concertPage);

		// === 🚀 When & Then ===
		// 파라미터 없이 요청할 때 기본값이 적용되는지 확인
		mockMvc.perform(get("/api/concerts"))  // 📄 파라미터 없음 (기본값 사용)
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		// === 🔍 호출 검증 ===
		// 기본값(0, 20)으로 호출되었는지 확인
		verify(concertService).getAllConcerts(0, 20);
	}

	// 🔧 **수정됨**: 캐시 Mock 최소화, 실제 서비스 로직에 집중
	@Test
	@Tag("api")
	@DisplayName("콘서트 검색 성공 - 서비스 로직 중심")
	void searchConcerts_ServiceCall() throws Exception {
		// === 🎯 Given - 테스트 조건 설정 ===
		// 캐시 Mock 최소화: 핵심 비즈니스 로직(검색)에 집중
		String searchKeyword = "BTS";
		given(concertService.searchConcerts(any(ConcertSearchDTO.class)))
			.willReturn(testConcertList);   // 📋 미리 준비된 검색 결과 반환

		// === 🚀 When & Then ===
		// 검색 파라미터가 있는 GET 요청 수행
		// 컨트롤러의 @GetMapping(params = "search") 매핑과 일치
		mockMvc.perform(get("/api/concerts")
				.param("search", searchKeyword))  // 🔍 search 파라미터로 "BTS" 전달
			.andDo(print())                       // 🔍 요청/응답 내용 콘솔 출력
			.andExpect(status().isOk())           // ✅ HTTP 200 상태 코드 확인
			.andExpect(jsonPath("$.success").value(true))         // ✅ 성공 응답 확인
			.andExpect(jsonPath("$.data").isArray())             // ✅ 데이터가 배열 형태인지 확인
			.andExpect(jsonPath("$.data.length()").value(2));    // ✅ 검색 결과 개수가 2개인지 확인

		// === 🔍 핵심 비즈니스 로직 검증 ===
		// concertService.searchConcerts가 정확히 한 번 호출되었는지 확인
		// 이것이 이 테스트의 핵심: 실제 검색 로직이 호출되는가?
		verify(concertService).searchConcerts(any(ConcertSearchDTO.class));
	}

	// 🔧 **수정됨**: 캐시 테스트를 별도로 분리, 복잡한 Mock 시나리오 제거
	@Test
	@Tag("api")
	@Tag("cache")
	@DisplayName("콘서트 검색 - 캐시 동작 기본 검증")
	void searchConcerts_CacheInteraction() throws Exception {
		// === 🎯 Given ===
		// 캐시 미스 상황만 간단히 테스트 (복잡한 캐시 로직은 Integration Test에서)
		String searchKeyword = "IU";
		given(cacheService.getCachedSearchResults(searchKeyword, ConcertDTO.class))
			.willReturn(Optional.empty());  // 🔍 캐시 미스 시뮬레이션
		given(concertService.searchConcerts(any(ConcertSearchDTO.class)))
			.willReturn(testConcertList);

		// === 🚀 When & Then ===
		mockMvc.perform(get("/api/concerts")
				.param("search", searchKeyword))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		// === 🔍 호출 검증 - 캐시 미스 시나리오 ===
		// 캐시 조회 → 서비스 호출 → 캐시 저장 순서 확인
		verify(cacheService).getCachedSearchResults(searchKeyword, ConcertDTO.class);
		verify(concertService).searchConcerts(any(ConcertSearchDTO.class));
		verify(cacheService).cacheSearchResults(searchKeyword, testConcertList);
	}

	// 🔧 **수정됨**: 매핑 조건을 정확히 테스트
	@Test
	@Tag("api")
	@DisplayName("콘서트 필터링 - 모든 필수 파라미터 포함")
	void filterConcerts_WithAllRequiredParams() throws Exception {
		// === 🎯 Given ===
		// Mock 서비스 동작 정의: searchConcerts가 호출되면 미리 준비된 테스트 데이터 반환
		given(concertService.searchConcerts(any(ConcertSearchDTO.class)))
			.willReturn(testConcertList);

		// === 🚀 When & Then ===
		// 실제 매핑 조건에 맞게 모든 필수 파라미터 포함
		// 컨트롤러의 @GetMapping(params = {"date", "price_min", "price_max"})와 정확히 일치
		mockMvc.perform(get("/api/concerts")
				.param("date", "2024-12-25")      // ✅ 첫 번째 필수 파라미터
				.param("price_min", "50000")      // ✅ 두 번째 필수 파라미터
				.param("price_max", "150000"))    // ✅ 세 번째 필수 파라미터
			.andDo(print())                       // 🔍 실제 요청/응답 콘솔 출력 (디버깅용)
			.andExpect(status().isOk())           // ✅ HTTP 200 상태 코드 확인
			.andExpect(jsonPath("$.success").value(true))           // ✅ 응답 JSON의 success 필드 확인
			.andExpect(jsonPath("$.data").isArray())               // ✅ data 필드가 배열인지 확인
			.andExpect(jsonPath("$.data.length()").value(2));      // ✅ 배열 길이가 2인지 확인

		// === 🔍 호출 검증 ===
		// concertService.searchConcerts가 정확히 한 번 호출되었는지 확인
		verify(concertService).searchConcerts(any(ConcertSearchDTO.class));
	}

	// 🆕 **새로 추가됨**: 매핑 조건 불일치 테스트
	@Test
	@Tag("api")
	@DisplayName("콘서트 필터링 실패 - 일부 파라미터 누락시 기본 매핑으로")
	void filterConcerts_MissingParams_FallbackToDefault() throws Exception {
		// === 🎯 Given ===
		// 기본 매핑(전체 목록 조회)이 호출될 것으로 예상
		Page<ConcertDTO> concertPage = new PageImpl<>(testConcertList);
		given(concertService.getAllConcerts(0, 20)).willReturn(concertPage);

		// === 🚀 When & Then ===
		// 일부 파라미터만 있을 때의 동작 테스트
		// 컨트롤러의 매핑 조건(3개 파라미터 모두 필요)에 맞지 않는 상황
		mockMvc.perform(get("/api/concerts")
				.param("date", "2024-12-25"))     // ❌ price_min, price_max 누락
			.andDo(print())                       // 🔍 실제 요청/응답 콘솔 출력
			.andExpect(status().isOk())           // ✅ 기본 매핑으로 리다이렉트되어 200 응답
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.content").isArray()); // 기본 매핑은 Page 객체 반환

		// === 🔍 호출 검증 ===
		// filterConcerts 매핑이 안 되므로 searchConcerts가 호출되지 않아야 함
		verify(concertService, never()).searchConcerts(any(ConcertSearchDTO.class));
		// 대신 기본 매핑의 getAllConcerts가 호출됨
		verify(concertService).getAllConcerts(0, 20);
	}

	@Test
	@Tag("api")
	@DisplayName("콘서트 상세 조회 성공")
	void getConcertDetail_Success() throws Exception {
		// === 🎯 Given ===
		Long concertId = 1L;
		// 🔧 **수정됨**: 캐시 로직 단순화
		given(cacheService.getCachedConcertDetail(concertId, ConcertDTO.class))
			.willReturn(Optional.empty());  // 캐시 미스로 단순화
		given(concertService.getConcertById(concertId))
			.willReturn(Optional.of(testConcertDTO));

		// === 🚀 When & Then ===
		mockMvc.perform(get("/api/concerts/{id}", concertId))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.concertId").value(concertId))
			.andExpect(jsonPath("$.data.title").value("테스트 콘서트"));

		// === 🔍 호출 순서 검증 ===
		// 캐시 조회 → 서비스 호출 → 캐시 저장 순서 확인
		verify(cacheService).getCachedConcertDetail(concertId, ConcertDTO.class);
		verify(concertService).getConcertById(concertId);
		verify(cacheService).cacheConcertDetail(concertId, testConcertDTO);
	}

	@Test
	@Tag("api")
	@DisplayName("콘서트 상세 조회 - 존재하지 않는 콘서트")
	void getConcertDetail_NotFound() throws Exception {
		// === 🎯 Given - 존재하지 않는 콘서트 시나리오 ===
		Long concertId = 999L;
		given(cacheService.getCachedConcertDetail(concertId, ConcertDTO.class))
			.willReturn(Optional.empty());  // 캐시에도 없음
		given(concertService.getConcertById(concertId))
			.willReturn(Optional.empty());  // 서비스에서도 찾을 수 없음

		// === 🚀 When & Then ===
		mockMvc.perform(get("/api/concerts/{id}", concertId))
			.andDo(print())
			.andExpect(status().isNotFound())     // ✅ HTTP 404 상태 코드 확인
			.andExpect(jsonPath("$.success").value(true))  // 🔍 SuccessResponse 구조 확인
			.andExpect(jsonPath("$.data").doesNotExist()); // ✅ data가 null인지 확인

		// === 🔍 호출 검증 ===
		verify(cacheService).getCachedConcertDetail(concertId, ConcertDTO.class);
		verify(concertService).getConcertById(concertId);
		// 캐시 저장은 호출되지 않아야 함 (데이터가 없으므로)
		verify(cacheService, never()).cacheConcertDetail(anyLong(), any());
	}

	@Test
	@Tag("api")
	@DisplayName("AI 요약 정보 조회 성공")
	void getAiSummary_Success() throws Exception {
		// === 🎯 Given ===
		Long concertId = 1L;
		String aiSummary = "AI가 생성한 콘서트 요약 정보입니다.";
		given(concertService.getAiSummary(concertId)).willReturn(aiSummary);

		// === 🚀 When & Then ===
		mockMvc.perform(get("/api/concerts/{id}/ai-summary", concertId))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data").value(aiSummary));  // ✅ AI 요약 내용 확인

		// === 🔍 호출 검증 ===
		verify(concertService).getAiSummary(concertId);
	}

	@Test
	@Tag("api")
	@DisplayName("콘서트 후기 조회 성공")
	void getConcertReviews_Success() throws Exception {
		// === 🎯 Given ===
		Long concertId = 1L;
		List<ReviewDTO> reviews = Arrays.asList(testReviewDTO);
		Page<ReviewDTO> reviewPage = new PageImpl<>(reviews);
		// 🔧 **수정됨**: eq() 매처 사용으로 정확한 파라미터 검증
		given(reviewService.getConcertReviews(eq(concertId), any()))
			.willReturn(reviewPage);

		// === 🚀 When & Then ===
		mockMvc.perform(get("/api/concerts/{id}/reviews", concertId)
				.param("page", "0")     // 📄 페이지 번호
				.param("size", "10"))   // 📄 페이지 크기
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data").exists())                        // ✅ data 필드 존재 확인
			.andExpect(jsonPath("$.data.content").isArray())              // ✅ content가 배열인지 확인
			.andExpect(jsonPath("$.data.content.length()").value(1))      // ✅ 후기 개수 확인
			.andExpect(jsonPath("$.data.content[0].title").value("테스트 후기")); // ✅ 후기 제목 확인

		// === 🔍 호출 검증 ===
		// concertId와 Pageable 객체가 정확히 전달되었는지 확인
		verify(reviewService).getConcertReviews(eq(concertId), any());
	}

	// ========== Helper Methods ==========
	private ConcertDTO createTestConcertDTO(Long id, String title, String artist) {
		ConcertDTO concertDTO = new ConcertDTO();
		concertDTO.setConcertId(id);
		concertDTO.setTitle(title);
		concertDTO.setArtist(artist);
		concertDTO.setDescription("테스트 설명");
		concertDTO.setSellerId(1L);
		concertDTO.setVenueName("테스트 공연장");
		concertDTO.setVenueAddress("테스트 주소");
		concertDTO.setConcertDate(LocalDate.now().plusDays(30));
		concertDTO.setStartTime(LocalTime.of(19, 0));
		concertDTO.setEndTime(LocalTime.of(21, 0));
		concertDTO.setTotalSeats(100);
		concertDTO.setBookingStartDate(LocalDateTime.now().plusDays(1));
		concertDTO.setBookingEndDate(LocalDateTime.now().plusDays(29));
		concertDTO.setMinAge(0);
		concertDTO.setMaxTicketsPerUser(4);
		concertDTO.setStatus(ConcertStatus.ON_SALE);
		concertDTO.setPosterImageUrl("https://example.com/poster.jpg");
		concertDTO.setAiSummary("AI 요약");
		return concertDTO;
	}

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
}