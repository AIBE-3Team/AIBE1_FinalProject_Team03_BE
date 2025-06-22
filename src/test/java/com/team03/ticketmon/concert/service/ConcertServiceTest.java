package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.dto.ConcertDTO;
import com.team03.ticketmon.concert.dto.ConcertFilterDTO;
import com.team03.ticketmon.concert.dto.ConcertSearchDTO;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConcertService 단위 테스트")
class ConcertServiceTest {

	@Mock
	private ConcertRepository concertRepository;

	@Mock
	private ConcertSeatRepository concertSeatRepository;

	@InjectMocks
	private ConcertService concertService;

	private Concert testConcert;
	private ConcertDTO testConcertDTO;

	@BeforeEach
	void setUp() {
		testConcert = createTestConcert();
		testConcertDTO = createTestConcertDTO();
	}

	@Nested
	@DisplayName("전체 콘서트 조회 테스트")
	class GetAllConcertsTest {

		@Test
		@DisplayName("정상적인 페이징 파라미터로 콘서트 목록 조회 성공")
		void getAllConcerts_WithValidParams_ShouldReturnPagedConcerts() {
			// given
			int page = 0;
			int size = 20;
			Pageable pageable = PageRequest.of(page, size);
			Page<Concert> concertPage = new PageImpl<>(Arrays.asList(testConcert), pageable, 1);

			given(concertRepository.findByStatusInOrderByConcertDateAsc(any(), eq(pageable)))
				.willReturn(concertPage);

			// when
			Page<ConcertDTO> result = concertService.getAllConcerts(page, size);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getContent()).hasSize(1);
			assertThat(result.getContent().get(0).getTitle()).isEqualTo("테스트 콘서트");
			verify(concertRepository).findByStatusInOrderByConcertDateAsc(any(), eq(pageable));
		}

		@Test
		@DisplayName("잘못된 페이지 번호로 조회 시 예외 발생")
		void getAllConcerts_WithInvalidPageNumber_ShouldThrowException() {
			// given
			int invalidPage = -1;
			int size = 20;

			// when & then
			assertThatThrownBy(() -> concertService.getAllConcerts(invalidPage, size))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_PAGE_NUMBER);
		}

		@Test
		@DisplayName("잘못된 페이지 크기로 조회 시 예외 발생")
		void getAllConcerts_WithInvalidPageSize_ShouldThrowException() {
			// given
			int page = 0;
			int invalidSize = 0;

			// when & then
			assertThatThrownBy(() -> concertService.getAllConcerts(page, invalidSize))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_PAGE_SIZE);
		}

		@Test
		@DisplayName("최대 페이지 크기 초과 시 예외 발생")
		void getAllConcerts_WithOversizedPage_ShouldThrowException() {
			// given
			int page = 0;
			int oversizedPage = 101;

			// when & then
			assertThatThrownBy(() -> concertService.getAllConcerts(page, oversizedPage))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_PAGE_SIZE);
		}
	}

	@Nested
	@DisplayName("콘서트 검색 테스트")
	class SearchConcertsTest {

		@Test
		@DisplayName("유효한 키워드로 검색 성공")
		void searchByKeyword_WithValidKeyword_ShouldReturnConcerts() {
			// given
			String keyword = "테스트";
			given(concertRepository.findByKeyword(keyword))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.searchByKeyword(keyword);

			// then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getTitle()).isEqualTo("테스트 콘서트");
			verify(concertRepository).findByKeyword(keyword);
		}

		@Test
		@DisplayName("공백 키워드로 검색 시 예외 발생")
		void searchByKeyword_WithBlankKeyword_ShouldThrowException() {
			// given
			String blankKeyword = "   ";

			// when & then
			assertThatThrownBy(() -> concertService.searchByKeyword(blankKeyword))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_SEARCH_KEYWORD);
		}

		@Test
		@DisplayName("null 키워드로 검색 시 예외 발생")
		void searchByKeyword_WithNullKeyword_ShouldThrowException() {
			// when & then
			assertThatThrownBy(() -> concertService.searchByKeyword(null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_SEARCH_KEYWORD);
		}

		@Test
		@DisplayName("ConcertSearchDTO로 검색 성공")
		void searchConcerts_WithValidDTO_ShouldReturnConcerts() {
			// given
			ConcertSearchDTO searchDTO = new ConcertSearchDTO("테스트");
			given(concertRepository.findByKeyword("테스트"))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.searchConcerts(searchDTO);

			// then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getTitle()).isEqualTo("테스트 콘서트");
			verify(concertRepository).findByKeyword("테스트");
		}

		@Test
		@DisplayName("null DTO로 검색 시 예외 발생")
		void searchConcerts_WithNullDTO_ShouldThrowException() {
			// when & then
			assertThatThrownBy(() -> concertService.searchConcerts(null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.SEARCH_CONDITION_REQUIRED);
		}
	}

	@Nested
	@DisplayName("날짜 필터링 테스트")
	class DateFilterTest {

		@Test
		@DisplayName("유효한 날짜 범위로 필터링 성공")
		void filterByDateRange_WithValidRange_ShouldReturnConcerts() {
			// given
			LocalDate startDate = LocalDate.of(2024, 1, 1);
			LocalDate endDate = LocalDate.of(2024, 12, 31);
			given(concertRepository.findByDateRange(startDate, endDate))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.filterByDateRange(startDate, endDate);

			// then
			assertThat(result).hasSize(1);
			verify(concertRepository).findByDateRange(startDate, endDate);
		}

		@Test
		@DisplayName("시작일이 종료일보다 늦을 때 예외 발생")
		void filterByDateRange_WithInvalidRange_ShouldThrowException() {
			// given
			LocalDate startDate = LocalDate.of(2024, 12, 31);
			LocalDate endDate = LocalDate.of(2024, 1, 1);

			// when & then
			assertThatThrownBy(() -> concertService.filterByDateRange(startDate, endDate))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_DATE_ORDER);
		}

		@Test
		@DisplayName("null 날짜로 필터링 시 정상 처리")
		void filterByDateRange_WithNullDates_ShouldWork() {
			// given
			given(concertRepository.findByDateRange(null, null))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.filterByDateRange(null, null);

			// then
			assertThat(result).hasSize(1);
			verify(concertRepository).findByDateRange(null, null);
		}
	}

	@Nested
	@DisplayName("가격 필터링 테스트")
	class PriceFilterTest {

		@Test
		@DisplayName("유효한 가격 범위로 필터링 성공")
		void filterByPriceRange_WithValidRange_ShouldReturnConcerts() {
			// given
			BigDecimal minPrice = new BigDecimal("10000");
			BigDecimal maxPrice = new BigDecimal("50000");
			given(concertRepository.findByPriceRange(minPrice, maxPrice))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.filterByPriceRange(minPrice, maxPrice);

			// then
			assertThat(result).hasSize(1);
			verify(concertRepository).findByPriceRange(minPrice, maxPrice);
		}

		@Test
		@DisplayName("음수 최소 가격으로 필터링 시 예외 발생")
		void filterByPriceRange_WithNegativeMinPrice_ShouldThrowException() {
			// given
			BigDecimal minPrice = new BigDecimal("-1000");
			BigDecimal maxPrice = new BigDecimal("50000");

			// when & then
			assertThatThrownBy(() -> concertService.filterByPriceRange(minPrice, maxPrice))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_PRICE_RANGE);
		}

		@Test
		@DisplayName("최소 가격이 최대 가격보다 클 때 예외 발생")
		void filterByPriceRange_WithInvalidRange_ShouldThrowException() {
			// given
			BigDecimal minPrice = new BigDecimal("50000");
			BigDecimal maxPrice = new BigDecimal("10000");

			// when & then
			assertThatThrownBy(() -> concertService.filterByPriceRange(minPrice, maxPrice))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_PRICE_RANGE);
		}
	}

	@Nested
	@DisplayName("콘서트 상세 조회 테스트")
	class GetConcertByIdTest {

		@Test
		@DisplayName("유효한 ID로 콘서트 조회 성공")
		void getConcertById_WithValidId_ShouldReturnConcert() {
			// given
			Long concertId = 1L;
			given(concertRepository.findById(concertId))
				.willReturn(Optional.of(testConcert));

			// when
			Optional<ConcertDTO> result = concertService.getConcertById(concertId);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().getTitle()).isEqualTo("테스트 콘서트");
			verify(concertRepository).findById(concertId);
		}

		@Test
		@DisplayName("존재하지 않는 ID로 조회 시 빈 Optional 반환")
		void getConcertById_WithNonExistentId_ShouldReturnEmpty() {
			// given
			Long concertId = 999L;
			given(concertRepository.findById(concertId))
				.willReturn(Optional.empty());

			// when
			Optional<ConcertDTO> result = concertService.getConcertById(concertId);

			// then
			assertThat(result).isEmpty();
			verify(concertRepository).findById(concertId);
		}

		@Test
		@DisplayName("null ID로 조회 시 예외 발생")
		void getConcertById_WithNullId_ShouldThrowException() {
			// when & then
			assertThatThrownBy(() -> concertService.getConcertById(null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_CONCERT_ID);
		}

		@Test
		@DisplayName("음수 ID로 조회 시 예외 발생")
		void getConcertById_WithNegativeId_ShouldThrowException() {
			// when & then
			assertThatThrownBy(() -> concertService.getConcertById(-1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_CONCERT_ID);
		}
	}

	@Nested
	@DisplayName("AI 요약 조회 테스트")
	class GetAiSummaryTest {

		@Test
		@DisplayName("AI 요약이 있는 콘서트 조회 성공")
		void getAiSummary_WithExistingSummary_ShouldReturnSummary() {
			// given
			Long concertId = 1L;
			testConcert.setAiSummary("AI가 생성한 요약입니다.");
			given(concertRepository.findById(concertId))
				.willReturn(Optional.of(testConcert));

			// when
			String result = concertService.getAiSummary(concertId);

			// then
			assertThat(result).isEqualTo("AI가 생성한 요약입니다.");
			verify(concertRepository).findById(concertId);
		}

		@Test
		@DisplayName("AI 요약이 없는 콘서트 조회 시 기본 메시지 반환")
		void getAiSummary_WithoutSummary_ShouldReturnDefaultMessage() {
			// given
			Long concertId = 1L;
			testConcert.setAiSummary(null);
			given(concertRepository.findById(concertId))
				.willReturn(Optional.of(testConcert));

			// when
			String result = concertService.getAiSummary(concertId);

			// then
			assertThat(result).isEqualTo("AI 요약 정보가 아직 생성되지 않았습니다.");
			verify(concertRepository).findById(concertId);
		}

		@Test
		@DisplayName("존재하지 않는 콘서트 ID로 AI 요약 조회 시 기본 메시지 반환")
		void getAiSummary_WithNonExistentId_ShouldReturnDefaultMessage() {
			// given
			Long concertId = 999L;
			given(concertRepository.findById(concertId))
				.willReturn(Optional.empty());

			// when
			String result = concertService.getAiSummary(concertId);

			// then
			assertThat(result).isEqualTo("AI 요약 정보가 아직 생성되지 않았습니다.");
			verify(concertRepository).findById(concertId);
		}
	}

	@Nested
	@DisplayName("필터 적용 테스트")
	class ApplyFiltersTest {

		@Test
		@DisplayName("null 필터로 조회 시 전체 콘서트 반환")
		void applyFilters_WithNullFilter_ShouldReturnAllConcerts() {
			// given
			given(concertRepository.findByStatusInOrderByConcertDateAsc(any()))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.applyFilters(null);

			// then
			assertThat(result).hasSize(1);
			verify(concertRepository).findByStatusInOrderByConcertDateAsc(any());
		}

		@Test
		@DisplayName("날짜와 가격 필터 모두 적용")
		void applyFilters_WithDateAndPriceFilter_ShouldApplyBothFilters() {
			// given
			ConcertFilterDTO filterDTO = new ConcertFilterDTO();
			filterDTO.setStartDate(LocalDate.of(2024, 1, 1));
			filterDTO.setEndDate(LocalDate.of(2024, 12, 31));
			filterDTO.setPriceMin(new BigDecimal("10000"));
			filterDTO.setPriceMax(new BigDecimal("50000"));

			given(concertRepository.findByDateAndPriceRange(any(), any(), any(), any()))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.applyFilters(filterDTO);

			// then
			assertThat(result).hasSize(1);
			verify(concertRepository).findByDateAndPriceRange(any(), any(), any(), any());
		}

		@Test
		@DisplayName("날짜 필터만 적용")
		void applyFilters_WithDateFilterOnly_ShouldApplyDateFilter() {
			// given
			ConcertFilterDTO filterDTO = new ConcertFilterDTO();
			filterDTO.setStartDate(LocalDate.of(2024, 1, 1));
			filterDTO.setEndDate(LocalDate.of(2024, 12, 31));

			given(concertRepository.findByDateRange(any(), any()))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.applyFilters(filterDTO);

			// then
			assertThat(result).hasSize(1);
			verify(concertRepository).findByDateRange(any(), any());
		}

		@Test
		@DisplayName("가격 필터만 적용")
		void applyFilters_WithPriceFilterOnly_ShouldApplyPriceFilter() {
			// given
			ConcertFilterDTO filterDTO = new ConcertFilterDTO();
			filterDTO.setPriceMin(new BigDecimal("10000"));
			filterDTO.setPriceMax(new BigDecimal("50000"));

			given(concertRepository.findByPriceRange(any(), any()))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.applyFilters(filterDTO);

			// then
			assertThat(result).hasSize(1);
			verify(concertRepository).findByPriceRange(any(), any());
		}
	}

	@Nested
	@DisplayName("예매 가능한 콘서트 조회 테스트")
	class GetBookableConcertsTest {

		@Test
		@DisplayName("예매 가능한 콘서트 목록 조회 성공")
		void getBookableConcerts_ShouldReturnBookableConcerts() {
			// given
			given(concertRepository.findBookableConcerts())
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.getBookableConcerts();

			// then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).getTitle()).isEqualTo("테스트 콘서트");
			verify(concertRepository).findBookableConcerts();
		}

		@Test
		@DisplayName("예매 가능한 콘서트가 없을 때 빈 리스트 반환")
		void getBookableConcerts_WithNoConcerts_ShouldReturnEmptyList() {
			// given
			given(concertRepository.findBookableConcerts())
				.willReturn(Collections.emptyList());

			// when
			List<ConcertDTO> result = concertService.getBookableConcerts();

			// then
			assertThat(result).isEmpty();
			verify(concertRepository).findBookableConcerts();
		}
	}

	@Nested
	@DisplayName("날짜별 콘서트 조회 테스트")
	class GetConcertsByDateTest {

		@Test
		@DisplayName("특정 날짜의 콘서트 조회 성공")
		void getConcertsByDate_WithValidDate_ShouldReturnConcerts() {
			// given
			LocalDate concertDate = LocalDate.of(2024, 6, 20);
			given(concertRepository.findByConcertDateAndStatusOrderByConcertDateAsc(
				concertDate, ConcertStatus.ON_SALE))
				.willReturn(Arrays.asList(testConcert));

			// when
			List<ConcertDTO> result = concertService.getConcertsByDate(concertDate);

			// then
			assertThat(result).hasSize(1);
			verify(concertRepository).findByConcertDateAndStatusOrderByConcertDateAsc(
				concertDate, ConcertStatus.ON_SALE);
		}

		@Test
		@DisplayName("null 날짜로 조회 시 예외 발생")
		void getConcertsByDate_WithNullDate_ShouldThrowException() {
			// when & then
			assertThatThrownBy(() -> concertService.getConcertsByDate(null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.CONCERT_DATE_REQUIRED);
		}
	}

	// ========== Helper Methods ==========

	private Concert createTestConcert() {
		Concert concert = new Concert();
		concert.setConcertId(1L);
		concert.setTitle("테스트 콘서트");
		concert.setArtist("테스트 아티스트");
		concert.setDescription("테스트 설명");
		concert.setSellerId(1L);
		concert.setVenueName("테스트 공연장");
		concert.setVenueAddress("서울시 강남구");
		concert.setConcertDate(LocalDate.of(2024, 6, 20));
		concert.setStartTime(LocalTime.of(19, 0));
		concert.setEndTime(LocalTime.of(21, 0));
		concert.setTotalSeats(1000);
		concert.setBookingStartDate(LocalDateTime.of(2024, 1, 1, 9, 0));
		concert.setBookingEndDate(LocalDateTime.of(2024, 6, 19, 23, 59));
		concert.setMinAge(0);
		concert.setMaxTicketsPerUser(4);
		concert.setStatus(ConcertStatus.ON_SALE);
		concert.setPosterImageUrl("http://example.com/poster.jpg");
		concert.setAiSummary("AI 요약");
		return concert;
	}

	private ConcertDTO createTestConcertDTO() {
		return new ConcertDTO(
			1L,
			"테스트 콘서트",
			"테스트 아티스트",
			"테스트 설명",
			1L,
			"테스트 공연장",
			"서울시 강남구",
			LocalDate.of(2024, 6, 20),
			LocalTime.of(19, 0),
			LocalTime.of(21, 0),
			1000,
			LocalDateTime.of(2024, 1, 1, 9, 0),
			LocalDateTime.of(2024, 6, 19, 23, 59),
			0,
			4,
			ConcertStatus.ON_SALE,
			"http://example.com/poster.jpg",
			"AI 요약"
		);
	}
}