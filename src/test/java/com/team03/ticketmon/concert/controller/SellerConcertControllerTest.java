package com.team03.ticketmon.concert.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team03.ticketmon.concert.dto.*;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.service.SellerConcertService;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.mockito.ArgumentCaptor;

/**
 * SellerConcertController 통합 테스트
 * 기본 기능부터 유저 시나리오까지 포괄적으로 테스트
 */
@WebMvcTest(
	controllers = SellerConcertController.class,
	excludeAutoConfiguration = {
		SecurityAutoConfiguration.class
	}
)
@TestPropertySource(properties = {"spring.security.enabled=false"})
@DisplayName("판매자 콘서트 컨트롤러 테스트")
class SellerConcertControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private SellerConcertService sellerConcertService;

	private ObjectMapper objectMapper;

	// 테스트용 상수
	private static final Long VALID_SELLER_ID = 1L;
	private static final Long VALID_CONCERT_ID = 1L;
	private static final Long INVALID_SELLER_ID = -1L;
	private static final Long INVALID_CONCERT_ID = -1L;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
	}

	@Nested
	@DisplayName("콘서트 목록 조회 테스트")
	class GetSellerConcertsTest {

		@Test
		@DisplayName("성공: 판매자 콘서트 목록 조회 (페이징)")
		void getSellerConcerts_Success() throws Exception {
			// Given
			List<SellerConcertDTO> concerts = createMockConcertList();
			Page<SellerConcertDTO> concertPage = new PageImpl<>(concerts, PageRequest.of(0, 10), concerts.size());

			when(sellerConcertService.getSellerConcerts(eq(VALID_SELLER_ID), any(Pageable.class)))
				.thenReturn(concertPage);

			// When & Then
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content").isArray())
				.andExpect(jsonPath("$.data.content[0].title").value("테스트 콘서트 1"))
				.andExpect(jsonPath("$.data.content[0].artist").value("테스트 아티스트 1"))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.size").value(10));

			verify(sellerConcertService).getSellerConcerts(eq(VALID_SELLER_ID), any(Pageable.class));
		}

		@Test
		@DisplayName("실패: 유효하지 않은 판매자 ID")
		void getSellerConcerts_InvalidSellerId() throws Exception {
			// Given
			when(sellerConcertService.getSellerConcerts(eq(INVALID_SELLER_ID), any(Pageable.class)))
				.thenThrow(new BusinessException(ErrorCode.INVALID_SELLER_ID));

			// When & Then
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", INVALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(sellerConcertService).getSellerConcerts(eq(INVALID_SELLER_ID), any(Pageable.class));
		}

		@Test
		@DisplayName("성공: 빈 결과 반환")
		void getSellerConcerts_EmptyResult() throws Exception {
			// Given
			Page<SellerConcertDTO> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
			when(sellerConcertService.getSellerConcerts(eq(VALID_SELLER_ID), any(Pageable.class)))
				.thenReturn(emptyPage);

			// When & Then
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isEmpty())
				.andExpect(jsonPath("$.data.totalElements").value(0));
		}
	}

	@Nested
	@DisplayName("상태별 콘서트 조회 테스트")
	class GetSellerConcertsByStatusTest {

		@Test
		@DisplayName("성공: 예정된 콘서트 조회")
		void getSellerConcertsByStatus_Scheduled_Success() throws Exception {
			// Given
			List<SellerConcertDTO> scheduledConcerts = createMockConcertList();
			when(sellerConcertService.getSellerConcertsByStatus(VALID_SELLER_ID, ConcertStatus.SCHEDULED))
				.thenReturn(scheduledConcerts);

			// When & Then
			mockMvc.perform(get("/api/seller/concerts/status")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("status", "SCHEDULED")
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data[0].status").value("SCHEDULED"));

			verify(sellerConcertService).getSellerConcertsByStatus(VALID_SELLER_ID, ConcertStatus.SCHEDULED);
		}

		@Test
		@DisplayName("성공: 판매중인 콘서트 조회")
		void getSellerConcertsByStatus_OnSale_Success() throws Exception {
			// Given
			List<SellerConcertDTO> onSaleConcerts = createMockConcertListWithStatus(ConcertStatus.ON_SALE);
			when(sellerConcertService.getSellerConcertsByStatus(VALID_SELLER_ID, ConcertStatus.ON_SALE))
				.thenReturn(onSaleConcerts);

			// When & Then
			mockMvc.perform(get("/api/seller/concerts/status")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("status", "ON_SALE")
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("ON_SALE"));
		}

		@Test
		@DisplayName("실패: 유효하지 않은 상태값")
		void getSellerConcertsByStatus_InvalidStatus() throws Exception {
			// When & Then
			mockMvc.perform(get("/api/seller/concerts/status")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("status", "INVALID_STATUS")
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
		}
	}

	@Nested
	@DisplayName("콘서트 생성 테스트")
	class CreateConcertTest {

		@Test
		@DisplayName("성공: 콘서트 생성")
		void createConcert_Success() throws Exception {
			// Given
			SellerConcertCreateDTO createDTO = createValidConcertCreateDTO();
			SellerConcertDTO createdConcert = createMockConcertDTO();

			when(sellerConcertService.createConcert(eq(VALID_SELLER_ID), any(SellerConcertCreateDTO.class)))
				.thenReturn(createdConcert);

			// When & Then
			mockMvc.perform(post("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(createDTO)))
				.andDo(print())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("콘서트가 생성되었습니다."))
				.andExpect(jsonPath("$.data.title").value(createDTO.getTitle()))
				.andExpect(jsonPath("$.data.artist").value(createDTO.getArtist()));

			verify(sellerConcertService).createConcert(eq(VALID_SELLER_ID), any(SellerConcertCreateDTO.class));
		}

		@Test
		@DisplayName("실패: 필수 필드 누락 - 제목")
		void createConcert_MissingTitle() throws Exception {
			// Given
			SellerConcertCreateDTO createDTO = createValidConcertCreateDTO();
			createDTO.setTitle(null);

			// When & Then
			mockMvc.perform(post("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(createDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());

			verify(sellerConcertService, never()).createConcert(any(), any());
		}

		@Test
		@DisplayName("실패: 필수 필드 누락 - 아티스트")
		void createConcert_MissingArtist() throws Exception {
			// Given
			SellerConcertCreateDTO createDTO = createValidConcertCreateDTO();
			createDTO.setArtist("");

			// When & Then
			mockMvc.perform(post("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(createDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("실패: 잘못된 날짜 (과거 날짜)")
		void createConcert_PastDate() throws Exception {
			// Given
			SellerConcertCreateDTO createDTO = createValidConcertCreateDTO();
			createDTO.setConcertDate(LocalDate.now().minusDays(1));

			// When & Then
			mockMvc.perform(post("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(createDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("실패: 잘못된 시간 순서 (종료시간이 시작시간보다 빠름)")
		void createConcert_InvalidTimeOrder() throws Exception {
			// Given
			SellerConcertCreateDTO createDTO = createValidConcertCreateDTO();
			createDTO.setStartTime(LocalTime.of(20, 0));
			createDTO.setEndTime(LocalTime.of(19, 0));

			// When & Then
			mockMvc.perform(post("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(createDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("실패: 좌석수 초과 (100,000석 초과)")
		void createConcert_ExceedsMaxSeats() throws Exception {
			// Given
			SellerConcertCreateDTO createDTO = createValidConcertCreateDTO();
			createDTO.setTotalSeats(100001);

			// When & Then
			mockMvc.perform(post("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(createDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("실패: 잘못된 포스터 URL 형식")
		void createConcert_InvalidPosterUrl() throws Exception {
			// Given
			SellerConcertCreateDTO createDTO = createValidConcertCreateDTO();
			createDTO.setPosterImageUrl("invalid-url");

			// When & Then
			mockMvc.perform(post("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(createDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("콘서트 수정 테스트")
	class UpdateConcertTest {

		@Test
		@DisplayName("성공: 콘서트 수정")
		void updateConcert_Success() throws Exception {
			// Given
			SellerConcertUpdateDTO updateDTO = createValidConcertUpdateDTO();
			SellerConcertDTO updatedConcert = createMockConcertDTO();

			when(sellerConcertService.updateConcert(eq(VALID_SELLER_ID), eq(VALID_CONCERT_ID), any(SellerConcertUpdateDTO.class)))
				.thenReturn(updatedConcert);

			// When & Then
			mockMvc.perform(put("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(updateDTO)))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("콘서트가 수정되었습니다."));

			verify(sellerConcertService).updateConcert(eq(VALID_SELLER_ID), eq(VALID_CONCERT_ID), any(SellerConcertUpdateDTO.class));
		}

		@Test
		@DisplayName("성공: 부분 수정 (제목만)")
		void updateConcert_PartialUpdate_TitleOnly() throws Exception {
			// Given
			SellerConcertUpdateDTO updateDTO = SellerConcertUpdateDTO.builder()
				.title("수정된 콘서트 제목")
				.build();

			SellerConcertDTO updatedConcert = createMockConcertDTO();
			when(sellerConcertService.updateConcert(eq(VALID_SELLER_ID), eq(VALID_CONCERT_ID), any(SellerConcertUpdateDTO.class)))
				.thenReturn(updatedConcert);

			// When & Then
			mockMvc.perform(put("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(updateDTO)))
				.andDo(print())
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("실패: 권한 없음 (다른 판매자의 콘서트)")
		void updateConcert_NoPermission() throws Exception {
			// Given
			SellerConcertUpdateDTO updateDTO = createValidConcertUpdateDTO();
			when(sellerConcertService.updateConcert(eq(VALID_SELLER_ID), eq(VALID_CONCERT_ID), any(SellerConcertUpdateDTO.class)))
				.thenThrow(new BusinessException(ErrorCode.SELLER_PERMISSION_DENIED));

			// When & Then
			mockMvc.perform(put("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(updateDTO)))
				.andDo(print())
				.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("실패: 존재하지 않는 콘서트")
		void updateConcert_NotFound() throws Exception {
			// Given
			SellerConcertUpdateDTO updateDTO = createValidConcertUpdateDTO();
			when(sellerConcertService.updateConcert(eq(VALID_SELLER_ID), eq(999L), any(SellerConcertUpdateDTO.class)))
				.thenThrow(new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

			// When & Then
			mockMvc.perform(put("/api/seller/concerts/{concertId}", 999L)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(updateDTO)))
				.andDo(print())
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("실패: 빈 수정 데이터")
		void updateConcert_EmptyUpdate() throws Exception {
			// Given
			SellerConcertUpdateDTO updateDTO = SellerConcertUpdateDTO.builder().build();

			// When & Then
			mockMvc.perform(put("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(updateDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("포스터 이미지 업데이트 테스트")
	class UpdatePosterImageTest {

		@Test
		@DisplayName("성공: 포스터 이미지 업데이트")
		void updatePosterImage_Success() throws Exception {
			// Given
			SellerConcertImageUpdateDTO imageDTO = new SellerConcertImageUpdateDTO("https://example.com/poster.jpg");
			doNothing().when(sellerConcertService).updatePosterImage(VALID_SELLER_ID, VALID_CONCERT_ID, imageDTO);

			// When & Then
			mockMvc.perform(patch("/api/seller/concerts/{concertId}/poster", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(imageDTO)))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("포스터 이미지가 업데이트되었습니다."));

			verify(sellerConcertService).updatePosterImage(eq(VALID_SELLER_ID), eq(VALID_CONCERT_ID), any(SellerConcertImageUpdateDTO.class));
		}

		@Test
		@DisplayName("실패: 잘못된 이미지 URL 형식")
		void updatePosterImage_InvalidUrl() throws Exception {
			// Given
			SellerConcertImageUpdateDTO imageDTO = new SellerConcertImageUpdateDTO("invalid-url");

			// When & Then
			mockMvc.perform(patch("/api/seller/concerts/{concertId}/poster", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(imageDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("실패: 빈 URL")
		void updatePosterImage_EmptyUrl() throws Exception {
			// Given
			SellerConcertImageUpdateDTO imageDTO = new SellerConcertImageUpdateDTO("");

			// When & Then
			mockMvc.perform(patch("/api/seller/concerts/{concertId}/poster", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(imageDTO)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("콘서트 삭제/취소 테스트")
	class DeleteConcertTest {

		@Test
		@DisplayName("성공: 콘서트 취소")
		void deleteConcert_Success() throws Exception {
			// Given
			doNothing().when(sellerConcertService).cancelConcert(VALID_SELLER_ID, VALID_CONCERT_ID);

			// When & Then
			mockMvc.perform(delete("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("콘서트가 취소되었습니다."));

			verify(sellerConcertService).cancelConcert(VALID_SELLER_ID, VALID_CONCERT_ID);
		}

		@Test
		@DisplayName("실패: 권한 없음")
		void deleteConcert_NoPermission() throws Exception {
			// Given
			doThrow(new BusinessException(ErrorCode.SELLER_PERMISSION_DENIED))
				.when(sellerConcertService).cancelConcert(VALID_SELLER_ID, VALID_CONCERT_ID);

			// When & Then
			mockMvc.perform(delete("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("실패: 존재하지 않는 콘서트")
		void deleteConcert_NotFound() throws Exception {
			// Given
			doThrow(new BusinessException(ErrorCode.CONCERT_NOT_FOUND))
				.when(sellerConcertService).cancelConcert(VALID_SELLER_ID, 999L);

			// When & Then
			mockMvc.perform(delete("/api/seller/concerts/{concertId}", 999L)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("콘서트 개수 조회 테스트")
	class GetConcertCountTest {

		@Test
		@DisplayName("성공: 콘서트 개수 조회")
		void getConcertCount_Success() throws Exception {
			// Given
			when(sellerConcertService.getSellerConcertCount(VALID_SELLER_ID)).thenReturn(5L);

			// When & Then
			mockMvc.perform(get("/api/seller/concerts/count")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value(5));

			verify(sellerConcertService).getSellerConcertCount(VALID_SELLER_ID);
		}

		@Test
		@DisplayName("성공: 콘서트가 없는 경우")
		void getConcertCount_Zero() throws Exception {
			// Given
			when(sellerConcertService.getSellerConcertCount(VALID_SELLER_ID)).thenReturn(0L);

			// When & Then
			mockMvc.perform(get("/api/seller/concerts/count")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(0));
		}
	}

	@Nested
	@DisplayName("실제 유저 시나리오 테스트")
	class UserScenarioTest {

		@Test
		@DisplayName("시나리오: 판매자가 콘서트를 생성하고 관리하는 전체 플로우")
		void completeSellerWorkflow() throws Exception {
			// 1. 콘서트 생성
			SellerConcertCreateDTO createDTO = createValidConcertCreateDTO();
			SellerConcertDTO createdConcert = createMockConcertDTO();

			when(sellerConcertService.createConcert(eq(VALID_SELLER_ID), any(SellerConcertCreateDTO.class)))
				.thenReturn(createdConcert);

			mockMvc.perform(post("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(createDTO)))
				.andExpect(status().isCreated());

			// 2. 콘서트 목록 확인
			List<SellerConcertDTO> concerts = Arrays.asList(createdConcert);
			Page<SellerConcertDTO> concertPage = new PageImpl<>(concerts);

			when(sellerConcertService.getSellerConcerts(eq(VALID_SELLER_ID), any(Pageable.class)))
				.thenReturn(concertPage);

			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isNotEmpty());

			// 3. 콘서트 수정
			SellerConcertUpdateDTO updateDTO = SellerConcertUpdateDTO.builder()
				.title("수정된 콘서트 제목")
				.status(ConcertStatus.ON_SALE)
				.build();

			when(sellerConcertService.updateConcert(eq(VALID_SELLER_ID), eq(VALID_CONCERT_ID), any(SellerConcertUpdateDTO.class)))
				.thenReturn(createdConcert);

			mockMvc.perform(put("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(updateDTO)))
				.andExpect(status().isOk());

			// 4. 포스터 이미지 업데이트
			SellerConcertImageUpdateDTO imageDTO = new SellerConcertImageUpdateDTO("https://example.com/new-poster.jpg");
			doNothing().when(sellerConcertService).updatePosterImage(VALID_SELLER_ID, VALID_CONCERT_ID, imageDTO);

			mockMvc.perform(patch("/api/seller/concerts/{concertId}/poster", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(imageDTO)))
				.andExpect(status().isOk());

			// 5. 상태별 콘서트 조회 (ON_SALE 상태로 수정된 콘서트)
			SellerConcertDTO updatedConcertWithStatus = createMockConcertDTOWithStatus(ConcertStatus.ON_SALE);
			when(sellerConcertService.getSellerConcertsByStatus(VALID_SELLER_ID, ConcertStatus.ON_SALE))
				.thenReturn(Arrays.asList(updatedConcertWithStatus));

			mockMvc.perform(get("/api/seller/concerts/status")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("status", "ON_SALE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("ON_SALE"));

			// 6. 콘서트 개수 확인
			when(sellerConcertService.getSellerConcertCount(VALID_SELLER_ID)).thenReturn(1L);

			mockMvc.perform(get("/api/seller/concerts/count")
					.param("sellerId", VALID_SELLER_ID.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(1));

			// 7. 콘서트 취소
			doNothing().when(sellerConcertService).cancelConcert(VALID_SELLER_ID, VALID_CONCERT_ID);

			mockMvc.perform(delete("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("콘서트가 취소되었습니다."));

			// 모든 서비스 메서드가 호출되었는지 확인
			verify(sellerConcertService).createConcert(any(), any());
			verify(sellerConcertService).getSellerConcerts(any(), any());
			verify(sellerConcertService).updateConcert(any(), any(), any());
			verify(sellerConcertService).updatePosterImage(any(), any(), any());
			verify(sellerConcertService).getSellerConcertsByStatus(any(), any());
			verify(sellerConcertService).getSellerConcertCount(any());
			verify(sellerConcertService).cancelConcert(any(), any());
		}

		@Test
		@DisplayName("시나리오: 여러 판매자가 동시에 콘서트를 관리하는 경우")
		void multipleSellerScenario() throws Exception {
			Long seller1Id = 1L;
			Long seller2Id = 2L;

			// 판매자 1의 콘서트 목록
			List<SellerConcertDTO> seller1Concerts = createMockConcertList();
			Page<SellerConcertDTO> seller1Page = new PageImpl<>(seller1Concerts);
			when(sellerConcertService.getSellerConcerts(eq(seller1Id), any(Pageable.class)))
				.thenReturn(seller1Page);

			// 판매자 2의 콘서트 목록 (빈 목록)
			Page<SellerConcertDTO> seller2Page = new PageImpl<>(List.of());
			when(sellerConcertService.getSellerConcerts(eq(seller2Id), any(Pageable.class)))
				.thenReturn(seller2Page);

			// 판매자 1: 콘서트 2개 보유
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", seller1Id.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2));

			// 판매자 2: 콘서트 0개 보유
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", seller2Id.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0));

			// 판매자 2가 판매자 1의 콘서트를 수정하려고 시도 (실패해야 함)
			SellerConcertUpdateDTO updateDTO = SellerConcertUpdateDTO.builder()
				.title("권한 없는 수정 시도")
				.build();

			when(sellerConcertService.updateConcert(eq(seller2Id), eq(VALID_CONCERT_ID), any()))
				.thenThrow(new BusinessException(ErrorCode.SELLER_PERMISSION_DENIED));

			mockMvc.perform(put("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", seller2Id.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(updateDTO)))
				.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("시나리오: 콘서트 상태 변화에 따른 관리")
		void concertStatusLifecycleScenario() throws Exception {
			// 1. SCHEDULED 상태의 콘서트 생성
			SellerConcertDTO scheduledConcert = createMockConcertDTOWithStatus(ConcertStatus.SCHEDULED);
			when(sellerConcertService.getSellerConcertsByStatus(VALID_SELLER_ID, ConcertStatus.SCHEDULED))
				.thenReturn(Arrays.asList(scheduledConcert));

			mockMvc.perform(get("/api/seller/concerts/status")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("status", "SCHEDULED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("SCHEDULED"));

			// 2. ON_SALE 상태로 변경
			SellerConcertDTO onSaleConcert = createMockConcertDTOWithStatus(ConcertStatus.ON_SALE);
			SellerConcertUpdateDTO updateToOnSale = SellerConcertUpdateDTO.builder()
				.status(ConcertStatus.ON_SALE)
				.build();

			when(sellerConcertService.updateConcert(eq(VALID_SELLER_ID), eq(VALID_CONCERT_ID), any()))
				.thenReturn(onSaleConcert);

			mockMvc.perform(put("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(updateToOnSale)))
				.andExpect(status().isOk());

			// 3. ON_SALE 상태 콘서트 조회
			when(sellerConcertService.getSellerConcertsByStatus(VALID_SELLER_ID, ConcertStatus.ON_SALE))
				.thenReturn(Arrays.asList(onSaleConcert));

			mockMvc.perform(get("/api/seller/concerts/status")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("status", "ON_SALE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("ON_SALE"));

			// 4. CANCELLED 상태로 변경 (삭제)
			doNothing().when(sellerConcertService).cancelConcert(VALID_SELLER_ID, VALID_CONCERT_ID);

			mockMvc.perform(delete("/api/seller/concerts/{concertId}", VALID_CONCERT_ID)
					.param("sellerId", VALID_SELLER_ID.toString()))
				.andExpect(status().isOk());

			// 5. CANCELLED 상태 콘서트 조회
			SellerConcertDTO cancelledConcert = createMockConcertDTOWithStatus(ConcertStatus.CANCELLED);
			when(sellerConcertService.getSellerConcertsByStatus(VALID_SELLER_ID, ConcertStatus.CANCELLED))
				.thenReturn(Arrays.asList(cancelledConcert));

			mockMvc.perform(get("/api/seller/concerts/status")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("status", "CANCELLED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].status").value("CANCELLED"));
		}

		@Test
		@DisplayName("시나리오: 대량 콘서트 관리 (페이징 테스트)")
		void largeConcertManagementScenario() throws Exception {
			// 테스트 데이터 준비
			List<SellerConcertDTO> firstPageConcerts = createMockConcertListForPaging(10, 0);
			List<SellerConcertDTO> secondPageConcerts = createMockConcertListForPaging(10, 10);
			List<SellerConcertDTO> lastPageConcerts = createMockConcertListForPaging(5, 20);

			Page<SellerConcertDTO> firstPage = new PageImpl<>(firstPageConcerts, PageRequest.of(0, 10), 25);
			Page<SellerConcertDTO> secondPage = new PageImpl<>(secondPageConcerts, PageRequest.of(1, 10), 25);
			Page<SellerConcertDTO> lastPage = new PageImpl<>(lastPageConcerts, PageRequest.of(2, 10), 25);

			// 🔥 연속 호출을 위한 Mock 설정 - thenReturn을 여러 개 연결
			when(sellerConcertService.getSellerConcerts(eq(VALID_SELLER_ID), any(Pageable.class)))
				.thenReturn(firstPage)   // 첫 번째 호출
				.thenReturn(secondPage)  // 두 번째 호출
				.thenReturn(lastPage);   // 세 번째 호출

			// 첫 번째 페이지 (0-9)
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("page", "0")
					.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content").isArray())
				.andExpect(jsonPath("$.data.content.length()").value(10))
				.andExpect(jsonPath("$.data.totalElements").value(25))
				.andExpect(jsonPath("$.data.totalPages").value(3))
				.andExpect(jsonPath("$.data.first").value(true))
				.andExpect(jsonPath("$.data.last").value(false));

			// 두 번째 페이지 (10-19)
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("page", "1")
					.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(10))
				.andExpect(jsonPath("$.data.first").value(false))
				.andExpect(jsonPath("$.data.last").value(false));

			// 마지막 페이지 (20-24)
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.param("page", "2")
					.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(5))
				.andExpect(jsonPath("$.data.first").value(false))
				.andExpect(jsonPath("$.data.last").value(true));
		}
	}

	@Nested
	@DisplayName("에러 처리 및 예외 상황 테스트")
	class ErrorHandlingTest {

		@Test
		@DisplayName("잘못된 요청 파라미터 처리")
		void handleInvalidRequestParameters() throws Exception {
			// sellerId 누락 - MissingServletRequestParameterException 발생
			mockMvc.perform(get("/api/seller/concerts")
					.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));

			// 잘못된 sellerId 형식 - MethodArgumentTypeMismatchException 발생
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", "invalid-id")
					.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
		}

		@Test
		@DisplayName("JSON 파싱 에러 처리")
		void handleJsonParsingError() throws Exception {
			mockMvc.perform(post("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{ invalid json }"))
				.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("서비스 레이어 예외 전파 테스트")
		void handleServiceLayerExceptions() throws Exception {
			// BusinessException 처리
			when(sellerConcertService.getSellerConcerts(any(), any()))
				.thenThrow(new BusinessException(ErrorCode.INVALID_SELLER_ID));

			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString()))
				.andExpect(status().isBadRequest());

			// RuntimeException 처리
			when(sellerConcertService.getSellerConcertCount(any()))
				.thenThrow(new RuntimeException("Unexpected error"));

			mockMvc.perform(get("/api/seller/concerts/count")
					.param("sellerId", VALID_SELLER_ID.toString()))
				.andExpect(status().isInternalServerError());
		}
	}

	@Nested
	@DisplayName("디버깅용 테스트")
	class DebuggingTest {

		@Test
		@DisplayName("Mock 호출 확인 및 응답 구조 디버깅")
		void debugMockAndResponse() throws Exception {
			// Given
			List<SellerConcertDTO> concerts = createMockConcertList();
			Page<SellerConcertDTO> concertPage = new PageImpl<>(concerts, PageRequest.of(0, 10), concerts.size());

			// Mock 설정
			when(sellerConcertService.getSellerConcerts(eq(VALID_SELLER_ID), any(Pageable.class)))
				.thenReturn(concertPage);

			// When & Then
			mockMvc.perform(get("/api/seller/concerts")
					.param("sellerId", VALID_SELLER_ID.toString())
					.contentType(MediaType.APPLICATION_JSON))
				.andDo(print()) // 응답 전체를 출력해서 구조 확인
				.andExpect(status().isOk());

			// ArgumentCaptor로 실제 전달된 파라미터 확인
			ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
			verify(sellerConcertService).getSellerConcerts(eq(VALID_SELLER_ID), pageableCaptor.capture());

			Pageable capturedPageable = pageableCaptor.getValue();
			System.out.println("🚨 실제 전달된 Pageable: " + capturedPageable);
			System.out.println("🚨 Page Number: " + capturedPageable.getPageNumber());
			System.out.println("🚨 Page Size: " + capturedPageable.getPageSize());
		}
	}

	private SellerConcertCreateDTO createValidConcertCreateDTO() {
		return SellerConcertCreateDTO.builder()
			.title("테스트 콘서트")
			.artist("테스트 아티스트")
			.description("테스트 콘서트 설명")
			.venueName("테스트 공연장")
			.venueAddress("서울시 강남구 테스트로 123")
			.concertDate(LocalDate.now().plusDays(30))
			.startTime(LocalTime.of(19, 0))
			.endTime(LocalTime.of(21, 0))
			.totalSeats(1000)
			.bookingStartDate(LocalDateTime.now().plusDays(1))
			.bookingEndDate(LocalDateTime.now().plusDays(29))
			.minAge(0)
			.maxTicketsPerUser(4)
			.posterImageUrl("https://example.com/poster.jpg")
			.build();
	}

	private SellerConcertUpdateDTO createValidConcertUpdateDTO() {
		return SellerConcertUpdateDTO.builder()
			.title("수정된 콘서트 제목")
			.artist("수정된 아티스트")
			.description("수정된 설명")
			.venueName("수정된 공연장")
			.venueAddress("수정된 주소")
			.totalSeats(1200)
			.minAge(0)
			.maxTicketsPerUser(6)
			.status(ConcertStatus.ON_SALE)
			.posterImageUrl("https://example.com/updated-poster.jpg")
			.build();
	}

	private SellerConcertDTO createMockConcertDTO() {
		return SellerConcertDTO.builder()
			.concertId(VALID_CONCERT_ID)
			.title("테스트 콘서트")
			.artist("테스트 아티스트")
			.description("테스트 콘서트 설명")
			.sellerId(VALID_SELLER_ID)
			.venueName("테스트 공연장")
			.venueAddress("서울시 강남구 테스트로 123")
			.concertDate(LocalDate.now().plusDays(30))
			.startTime(LocalTime.of(19, 0))
			.endTime(LocalTime.of(21, 0))
			.totalSeats(1000)
			.bookingStartDate(LocalDateTime.now().plusDays(1))
			.bookingEndDate(LocalDateTime.now().plusDays(29))
			.minAge(0)
			.maxTicketsPerUser(4)
			.status(ConcertStatus.SCHEDULED)
			.posterImageUrl("https://example.com/poster.jpg")
			.aiSummary("AI 생성 요약")
			.createdAt(LocalDateTime.now().minusDays(1))
			.updatedAt(LocalDateTime.now().minusDays(1))
			.build();
	}

	private SellerConcertDTO createMockConcertDTOWithStatus(ConcertStatus status) {
		SellerConcertDTO concert = createMockConcertDTO();
		concert.setStatus(status);
		return concert;
	}

	private List<SellerConcertDTO> createMockConcertList() {
		return Arrays.asList(
			SellerConcertDTO.builder()
				.concertId(1L)
				.title("테스트 콘서트 1")
				.artist("테스트 아티스트 1")
				.sellerId(VALID_SELLER_ID)
				.status(ConcertStatus.SCHEDULED)
				.totalSeats(1000)
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(29))
				.createdAt(LocalDateTime.now().minusDays(2))
				.updatedAt(LocalDateTime.now().minusDays(2))
				.build(),
			SellerConcertDTO.builder()
				.concertId(2L)
				.title("테스트 콘서트 2")
				.artist("테스트 아티스트 2")
				.sellerId(VALID_SELLER_ID)
				.status(ConcertStatus.ON_SALE)
				.totalSeats(1500)
				.concertDate(LocalDate.now().plusDays(45))
				.startTime(LocalTime.of(20, 0))
				.endTime(LocalTime.of(22, 0))
				.bookingStartDate(LocalDateTime.now().plusDays(5))
				.bookingEndDate(LocalDateTime.now().plusDays(44))
				.createdAt(LocalDateTime.now().minusDays(1))
				.updatedAt(LocalDateTime.now().minusDays(1))
				.build()
		);
	}

	private List<SellerConcertDTO> createMockConcertListWithStatus(ConcertStatus status) {
		List<SellerConcertDTO> concerts = createMockConcertList();
		concerts.forEach(concert -> concert.setStatus(status));
		return concerts;
	}

	private List<SellerConcertDTO> createMockConcertListForPaging(int size, int startIndex) {
		List<SellerConcertDTO> concerts = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			int index = startIndex + i;
			concerts.add(SellerConcertDTO.builder()
				.concertId((long) (index + 1))
				.title("콘서트 " + (index + 1))
				.artist("아티스트 " + (index + 1))
				.sellerId(VALID_SELLER_ID)
				.status(ConcertStatus.SCHEDULED)
				.totalSeats(1000)
				.concertDate(LocalDate.now().plusDays(30 + index))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(29 + index))
				.createdAt(LocalDateTime.now().minusDays(index))
				.updatedAt(LocalDateTime.now().minusDays(index))
				.build());
		}
		return concerts;
	}
}