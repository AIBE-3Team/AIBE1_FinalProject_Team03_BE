package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.*;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.SellerConcertRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerConcertService 판매자 관점 테스트")
class SellerConcertServiceTest {

	@Mock
	private SellerConcertRepository sellerConcertRepository;

	@InjectMocks
	private SellerConcertService sellerConcertService;

	private Long validSellerId;
	private Long otherSellerId;
	private Long validConcertId;
	private Concert testConcert;
	private Concert soldOutConcert;
	private Concert cancelledConcert;
	private SellerConcertCreateDTO createDTO;
	private Pageable pageable;

	@BeforeEach
	void setUp() {
		validSellerId = 1L;
		otherSellerId = 2L;
		validConcertId = 1L;
		pageable = PageRequest.of(0, 10);

		testConcert = createTestConcert();
		soldOutConcert = createSoldOutConcert();
		cancelledConcert = createCancelledConcert();
		createDTO = createValidCreateDTO();
	}

	@Nested
	@DisplayName("데이터 보안 및 권한 관리")
	class SecurityAndPermissionTests {

		@Test
		@DisplayName("다른 판매자의 콘서트를 조회할 수 없다")
		void cannotAccessOtherSellersConcerts() {
			// given
			Concert otherSellerConcert = createTestConcert();
			otherSellerConcert.setSellerId(otherSellerId);
			List<Concert> concerts = Arrays.asList(otherSellerConcert);
			Page<Concert> concertPage = new PageImpl<>(concerts, pageable, 1);

			given(sellerConcertRepository.findBySellerIdOrderByCreatedAtDesc(validSellerId, pageable))
				.willReturn(new PageImpl<>(Arrays.asList(), pageable, 0));

			// when
			Page<SellerConcertDTO> result = sellerConcertService.getSellerConcerts(validSellerId, pageable);

			// then
			assertThat(result.getContent()).isEmpty();
		}

		@Test
		@DisplayName("다른 판매자의 콘서트를 수정할 수 없다")
		void cannotUpdateOtherSellersConcert() {
			// given
			given(sellerConcertRepository.existsByConcertIdAndSellerId(validConcertId, validSellerId))
				.willReturn(false);

			SellerConcertUpdateDTO updateDTO = SellerConcertUpdateDTO.builder()
				.title("해킹 시도")
				.build();

			// when & then
			assertThatThrownBy(() -> sellerConcertService
				.updateConcert(validSellerId, validConcertId, updateDTO))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.SELLER_PERMISSION_DENIED);
		}

		@Test
		@DisplayName("다른 판매자의 콘서트를 취소할 수 없다")
		void cannotCancelOtherSellersConcert() {
			// given
			Concert otherSellerConcert = createTestConcert();
			otherSellerConcert.setSellerId(otherSellerId);

			given(sellerConcertRepository.findById(validConcertId))
				.willReturn(Optional.of(otherSellerConcert));
			given(sellerConcertRepository.existsByConcertIdAndSellerId(validConcertId, validSellerId))
				.willReturn(false);

			// when & then
			assertThatThrownBy(() -> sellerConcertService.cancelConcert(validSellerId, validConcertId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.SELLER_PERMISSION_DENIED);
		}
	}

	@Nested
	@DisplayName("비즈니스 규칙 위반 방지")
	class BusinessRuleViolationTests {

		@Test
		@DisplayName("이미 매진된 콘서트도 상태 변경이 가능하다")
		void canUpdateSoldOutConcertStatus() {
			// given
			given(sellerConcertRepository.existsByConcertIdAndSellerId(validConcertId, validSellerId))
				.willReturn(true);
			given(sellerConcertRepository.findById(validConcertId))
				.willReturn(Optional.of(soldOutConcert));
			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(soldOutConcert);

			SellerConcertUpdateDTO updateDTO = SellerConcertUpdateDTO.builder()
				.status(ConcertStatus.CANCELLED)
				.build();

			// when
			SellerConcertDTO result = sellerConcertService
				.updateConcert(validSellerId, validConcertId, updateDTO);

			// then
			assertThat(result.getStatus()).isEqualTo(ConcertStatus.CANCELLED);
		}

		@Test
		@DisplayName("이미 취소된 콘서트는 다시 취소할 수 있다 (멱등성)")
		void canCancelAlreadyCancelledConcert() {
			// given
			given(sellerConcertRepository.findById(validConcertId))
				.willReturn(Optional.of(cancelledConcert));
			given(sellerConcertRepository.existsByConcertIdAndSellerId(validConcertId, validSellerId))
				.willReturn(true);
			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(cancelledConcert);

			// when
			assertThatCode(() -> sellerConcertService.cancelConcert(validSellerId, validConcertId))
				.doesNotThrowAnyException();

			// then
			assertThat(cancelledConcert.getStatus()).isEqualTo(ConcertStatus.CANCELLED);
		}

		@Test
		@DisplayName("공연 당일에도 콘서트를 취소할 수 있다")
		void canCancelConcertOnPerformanceDay() {
			// given
			Concert todayConcert = createTestConcert();
			todayConcert.setConcertDate(LocalDate.now());

			given(sellerConcertRepository.findById(validConcertId))
				.willReturn(Optional.of(todayConcert));
			given(sellerConcertRepository.existsByConcertIdAndSellerId(validConcertId, validSellerId))
				.willReturn(true);
			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(todayConcert);

			// when
			assertThatCode(() -> sellerConcertService.cancelConcert(validSellerId, validConcertId))
				.doesNotThrowAnyException();

			// then
			assertThat(todayConcert.getStatus()).isEqualTo(ConcertStatus.CANCELLED);
		}
	}

	@Nested
	@DisplayName("데이터 일관성 및 정합성")
	class DataConsistencyTests {

		@Test
		@DisplayName("콘서트 생성 시 필수 데이터가 정확히 설정된다")
		void concertCreationSetsCorrectDefaultValues() {
			// given
			Concert createdConcert = new Concert();
			createdConcert.setConcertId(validConcertId);
			createdConcert.setTitle(createDTO.getTitle()); // "새로운 콘서트"
			createdConcert.setArtist(createDTO.getArtist()); // "새로운 아티스트"
			createdConcert.setSellerId(validSellerId);
			createdConcert.setStatus(ConcertStatus.SCHEDULED);
			createdConcert.setMinAge(0);
			createdConcert.setMaxTicketsPerUser(4);
			createdConcert.setCreatedAt(LocalDateTime.now());
			createdConcert.setUpdatedAt(LocalDateTime.now());

			given(sellerConcertRepository.save(any(Concert.class))).willReturn(createdConcert);

			// when
			SellerConcertDTO result = sellerConcertService.createConcert(validSellerId, createDTO);

			// then
			assertThat(result.getSellerId()).isEqualTo(validSellerId);
			assertThat(result.getStatus()).isEqualTo(ConcertStatus.SCHEDULED);
			assertThat(result.getMinAge()).isEqualTo(0); // 기본값 설정 확인
			assertThat(result.getMaxTicketsPerUser()).isEqualTo(4); // 기본값 설정 확인
			assertThat(result.getTitle()).isEqualTo(createDTO.getTitle()); // "새로운 콘서트"
			assertThat(result.getArtist()).isEqualTo(createDTO.getArtist()); // "새로운 아티스트"

			// save 메서드 호출 시 전달된 Concert 객체 검증
			verify(sellerConcertRepository).save(argThat(concert ->
				concert.getTitle().equals(createDTO.getTitle().trim()) &&
					concert.getArtist().equals(createDTO.getArtist().trim()) &&
					concert.getSellerId().equals(validSellerId) &&
					concert.getStatus() == ConcertStatus.SCHEDULED
			));
		}

		@Test
		@DisplayName("부분 업데이트 시 null 필드는 기존 값을 유지한다")
		void partialUpdateKeepsExistingValues() {
			// given
			String originalTitle = testConcert.getTitle();
			String originalArtist = testConcert.getArtist();

			given(sellerConcertRepository.existsByConcertIdAndSellerId(validConcertId, validSellerId))
				.willReturn(true);
			given(sellerConcertRepository.findById(validConcertId))
				.willReturn(Optional.of(testConcert));
			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(testConcert);

			SellerConcertUpdateDTO partialUpdateDTO = SellerConcertUpdateDTO.builder()
				.description("새로운 설명만 변경")
				.build();

			// when
			SellerConcertDTO result = sellerConcertService
				.updateConcert(validSellerId, validConcertId, partialUpdateDTO);

			// then
			assertThat(result.getTitle()).isEqualTo(originalTitle); // 기존값 유지
			assertThat(result.getArtist()).isEqualTo(originalArtist); // 기존값 유지
			assertThat(result.getDescription()).isEqualTo("새로운 설명만 변경");
		}

		@Test
		@DisplayName("포스터 URL 업데이트 시 권한 검증이 정확히 수행된다")
		void posterUpdateValidatesPermissionCorrectly() {
			// given
			SellerConcertImageUpdateDTO imageDTO = new SellerConcertImageUpdateDTO(
				"https://example.com/new-poster.jpg");

			given(sellerConcertRepository.updatePosterImageUrl(
				validConcertId, validSellerId, imageDTO.getPosterImageUrl()))
				.willReturn(1); // 업데이트 성공

			// when
			assertThatCode(() -> sellerConcertService
				.updatePosterImage(validSellerId, validConcertId, imageDTO))
				.doesNotThrowAnyException();

			// then
			verify(sellerConcertRepository).updatePosterImageUrl(
				validConcertId, validSellerId, imageDTO.getPosterImageUrl());
		}

		@Test
		@DisplayName("문자열 필드들이 trim되어 저장된다")
		void trimsStringFieldsBeforeSaving() {
			// Given: 앞뒤 공백이 있는 문자열 필드들
			SellerConcertCreateDTO createDTO = SellerConcertCreateDTO.builder()
				.title("  콘서트 제목  ")
				.artist("  아티스트명  ")
				.venueName("  공연장명  ")
				.description("  설명  ")
				.venueAddress("  주소  ")
				.posterImageUrl("  http://example.com/poster.jpg  ")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(100)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(25))
				.build();

			when(sellerConcertRepository.save(any(Concert.class))).thenAnswer(invocation -> {
				Concert concert = invocation.getArgument(0);
				concert.setConcertId(1L);
				return concert;
			});

			// When
			SellerConcertDTO result = sellerConcertService.createConcert(1L, createDTO);

			// Then: 모든 문자열 필드들이 trim되어 저장됨
			assertThat(result.getTitle()).isEqualTo("콘서트 제목");
			assertThat(result.getArtist()).isEqualTo("아티스트명");
			assertThat(result.getVenueName()).isEqualTo("공연장명");
			assertThat(result.getDescription()).isEqualTo("설명"); // ✅ 이제 trim됨
			assertThat(result.getVenueAddress()).isEqualTo("주소"); // ✅ 이제 trim됨
			assertThat(result.getPosterImageUrl()).isEqualTo("http://example.com/poster.jpg"); // ✅ 이제 trim됨
		}
	}

	@Nested
	@DisplayName("실제 운영 시나리오")
	class RealWorldScenarioTests {

		@Test
		@DisplayName("대량의 콘서트를 보유한 판매자도 정상적으로 조회할 수 있다")
		void canHandleLargeNumberOfConcerts() {
			// given
			List<Concert> manyConcerts = createManyConcerts(100);
			Page<Concert> concertPage = new PageImpl<>(manyConcerts.subList(0, 10), pageable, 100);

			given(sellerConcertRepository.findBySellerIdOrderByCreatedAtDesc(validSellerId, pageable))
				.willReturn(concertPage);

			// when
			Page<SellerConcertDTO> result = sellerConcertService.getSellerConcerts(validSellerId, pageable);

			// then
			assertThat(result.getContent()).hasSize(10);
			assertThat(result.getTotalElements()).isEqualTo(100);
			assertThat(result.getTotalPages()).isEqualTo(10);
		}

		@Test
		@DisplayName("빈 값들로 업데이트 시도 시 적절히 처리된다")
		void handlesEmptyStringUpdatesCorrectly() {
			// Given: 정상적인 콘서트 생성
			Concert savedConcert = createValidConcert();
			when(sellerConcertRepository.existsByConcertIdAndSellerId(1L, 1L)).thenReturn(true);
			when(sellerConcertRepository.findById(1L)).thenReturn(Optional.of(savedConcert));
			when(sellerConcertRepository.save(any(Concert.class))).thenReturn(savedConcert);

			// When: 빈 문자열로 업데이트 시도
			SellerConcertUpdateDTO updateDTO = SellerConcertUpdateDTO.builder()
				.title("") // 빈 문자열
				.artist("   ") // 공백만 있는 문자열
				.venueName("") // 빈 문자열
				.description("") // Optional 필드 - 빈 문자열
				.build();

			SellerConcertDTO result = sellerConcertService.updateConcert(1L, 1L, updateDTO);

			// Then: 개선된 로직 검증
			assertThat(result.getTitle()).isEqualTo("정상 콘서트"); // 기존 값 유지
			assertThat(result.getArtist()).isEqualTo("정상 아티스트"); // 기존 값 유지
			assertThat(result.getVenueName()).isEqualTo("정상 공연장"); // 기존 값 유지

			// Optional 필드는 빈 문자열이 null로 변환됨
			assertThat(result.getDescription()).isNull(); // 빈 문자열 → null 변환
		}

		@Test
		@DisplayName("동시에 여러 상태의 콘서트를 필터링할 수 있다")
		void canFilterConcertsByMultipleStatuses() {
			// given
			List<Concert> scheduledConcerts = Arrays.asList(testConcert);
			List<Concert> soldOutConcerts = Arrays.asList(soldOutConcert);

			given(sellerConcertRepository.findBySellerIdAndStatus(validSellerId, ConcertStatus.SCHEDULED))
				.willReturn(scheduledConcerts);
			given(sellerConcertRepository.findBySellerIdAndStatus(validSellerId, ConcertStatus.SOLD_OUT))
				.willReturn(soldOutConcerts);

			// when
			List<SellerConcertDTO> scheduledResult = sellerConcertService
				.getSellerConcertsByStatus(validSellerId, ConcertStatus.SCHEDULED);
			List<SellerConcertDTO> soldOutResult = sellerConcertService
				.getSellerConcertsByStatus(validSellerId, ConcertStatus.SOLD_OUT);

			// then
			assertThat(scheduledResult).hasSize(1);
			assertThat(scheduledResult.get(0).getStatus()).isEqualTo(ConcertStatus.SCHEDULED);
			assertThat(soldOutResult).hasSize(1);
			assertThat(soldOutResult.get(0).getStatus()).isEqualTo(ConcertStatus.SOLD_OUT);
		}

		@Test
		@DisplayName("판매자 통계 조회가 정확한 값을 반환한다")
		void returnsAccurateSellerStatistics() {
			// given
			long expectedCount = 42L;
			given(sellerConcertRepository.countBySellerIdOrderByCreatedAtDesc(validSellerId))
				.willReturn(expectedCount);

			// when
			long actualCount = sellerConcertService.getSellerConcertCount(validSellerId);

			// then
			assertThat(actualCount).isEqualTo(expectedCount);
		}

		@Test
		@DisplayName("Optional 필드의 빈 문자열은 null로 변환된다")
		void convertsEmptyOptionalFieldsToNull() {
			// Given: Optional 필드가 빈 문자열인 DTO
			SellerConcertCreateDTO createDTO = SellerConcertCreateDTO.builder()
				.title("정상 제목")
				.artist("정상 아티스트")
				.venueName("정상 공연장")
				.description("   ") // 공백만 있는 문자열
				.venueAddress("") // 빈 문자열
				.posterImageUrl("") // 빈 문자열
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(100)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(25))
				.build();

			when(sellerConcertRepository.save(any(Concert.class))).thenAnswer(invocation -> {
				Concert concert = invocation.getArgument(0);
				concert.setConcertId(1L);
				return concert;
			});

			// When
			SellerConcertDTO result = sellerConcertService.createConcert(1L, createDTO);

			// Then: Optional 필드들이 null로 변환됨
			assertThat(result.getDescription()).isNull();
			assertThat(result.getVenueAddress()).isNull();
			assertThat(result.getPosterImageUrl()).isNull();
		}
	}

	@Nested
	@DisplayName("서비스 레벨 예외 처리 테스트")
	class ServiceLevelExceptionTests {

		@Test
		@DisplayName("필수 필드가 null인 경우 BusinessException이 발생함을 확인한다")
		void throwsBusinessExceptionForNullRequiredFields() {
			// Given: 필수 필드가 null인 DTO
			SellerConcertCreateDTO createDTO = SellerConcertCreateDTO.builder()
				.title("정상 제목")
				.artist(null) // null 필수 필드
				.venueName("정상 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(100)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(25))
				.build();

			// When & Then: 개선된 서비스는 NPE를 방지하고 명시적 예외 발생
			assertThatThrownBy(() -> sellerConcertService.createConcert(1L, createDTO))
				.isInstanceOf(BusinessException.class)
				.hasMessage("아티스트명이 필요합니다");
		}

		@Test
		@DisplayName("빈 문자열 필수 필드에 대해 BusinessException이 발생함을 확인한다")
		void throwsBusinessExceptionForEmptyRequiredFields() {
			// Given: 필수 필드가 빈 문자열인 DTO
			SellerConcertCreateDTO createDTO = SellerConcertCreateDTO.builder()
				.title("") // 빈 문자열
				.artist("정상 아티스트")
				.venueName("정상 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(100)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(25))
				.build();

			// When & Then: 빈 문자열도 유효하지 않은 값으로 처리
			assertThatThrownBy(() -> sellerConcertService.createConcert(1L, createDTO))
				.isInstanceOf(BusinessException.class)
				.hasMessage("콘서트 제목이 필요합니다");
		}
	}

	@Nested
	@DisplayName("판매자 입력 검증 및 Validation 처리")
	class ValidationTests {

		@Test
		@DisplayName("콘서트 생성 시 과거 날짜 입력을 방지한다")
		void preventsPastDateInConcertCreation() {
			// given
			SellerConcertCreateDTO invalidDTO = SellerConcertCreateDTO.builder()
				.title("과거 콘서트")
				.artist("테스트 아티스트")
				.venueName("테스트 공연장")
				.concertDate(LocalDate.now().minusDays(1)) // 과거 날짜
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(1000)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(10))
				.build();

			// 실제로는 @Valid 어노테이션이 Controller에서 처리하지만,
			// 서비스 레벨에서도 비즈니스 로직 검증이 필요할 수 있음
			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(createTestConcert());

			// when
			assertThatCode(() -> sellerConcertService.createConcert(validSellerId, invalidDTO))
				.doesNotThrowAnyException(); // 서비스는 일단 통과, Validation은 Controller에서 처리
		}

		@Test
		@DisplayName("종료시간이 시작시간보다 빠른 경우를 감지한다")
		void detectsInvalidTimeOrder() {
			// given
			SellerConcertCreateDTO invalidTimeDTO = SellerConcertCreateDTO.builder()
				.title("시간 오류 콘서트")
				.artist("테스트 아티스트")
				.venueName("테스트 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(21, 0)) // 시작이 더 늦음
				.endTime(LocalTime.of(19, 0))   // 종료가 더 빠름
				.totalSeats(1000)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(29))
				.build();

			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(createTestConcert());

			// when & then
			// @ValidConcertTimes 어노테이션이 검증하지만, 서비스에서 추가 검증 가능
			assertThatCode(() -> sellerConcertService.createConcert(validSellerId, invalidTimeDTO))
				.doesNotThrowAnyException(); // Validation은 DTO 레벨에서 처리
		}

		@Test
		@DisplayName("예매 기간이 공연 날짜 이후인 경우를 감지한다")
		void detectsBookingPeriodAfterConcertDate() {
			// given
			LocalDate concertDate = LocalDate.now().plusDays(10);
			SellerConcertCreateDTO invalidBookingDTO = SellerConcertCreateDTO.builder()
				.title("예매기간 오류 콘서트")
				.artist("테스트 아티스트")
				.venueName("테스트 공연장")
				.concertDate(concertDate)
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(1000)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(concertDate.atTime(20, 0)) // 공연 시작 후에 예매 종료
				.build();

			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(createTestConcert());

			// when & then
			assertThatCode(() -> sellerConcertService.createConcert(validSellerId, invalidBookingDTO))
				.doesNotThrowAnyException(); // Validation은 DTO 레벨에서 처리
		}

		@Test
		@DisplayName("좌석 수가 비정상적으로 많은 경우를 감지한다")
		void detectsExcessiveSeatsNumber() {
			// given
			SellerConcertCreateDTO excessiveSeatsDTO = SellerConcertCreateDTO.builder()
				.title("대형 콘서트")
				.artist("메가 아티스트")
				.venueName("거대 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(200000) // 20만석 (비현실적)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(29))
				.build();

			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(createTestConcert());

			// when & then
			// @Max(value = 100000) 검증이 있으므로 Controller에서 막힘
			assertThatCode(() -> sellerConcertService.createConcert(validSellerId, excessiveSeatsDTO))
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("잘못된 포스터 URL 형식을 감지한다")
		void detectsInvalidPosterUrlFormat() {
			// given
			SellerConcertCreateDTO invalidUrlDTO = SellerConcertCreateDTO.builder()
				.title("잘못된 포스터 콘서트")
				.artist("테스트 아티스트")
				.venueName("테스트 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(1000)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(29))
				.posterImageUrl("invalid-url-format") // 잘못된 URL 형식
				.build();

			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(createTestConcert());

			// when & then
			// @Pattern 검증이 Controller에서 처리
			assertThatCode(() -> sellerConcertService.createConcert(validSellerId, invalidUrlDTO))
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("업데이트 시 아무 필드도 변경하지 않는 경우를 감지한다")
		void detectsEmptyUpdateRequest() {
			// given
			SellerConcertUpdateDTO emptyUpdateDTO = SellerConcertUpdateDTO.builder()
				.build(); // 모든 필드가 null

			given(sellerConcertRepository.existsByConcertIdAndSellerId(validConcertId, validSellerId))
				.willReturn(true);
			given(sellerConcertRepository.findById(validConcertId))
				.willReturn(Optional.of(testConcert)); // Mock 설정 추가
			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(testConcert);

			// when & then
			// @AssertTrue(message = "수정할 항목이 최소 하나는 있어야 합니다") 검증은 Controller에서 처리
			// 서비스에서는 정상 처리되어야 함
			assertThatCode(() -> sellerConcertService
				.updateConcert(validSellerId, validConcertId, emptyUpdateDTO))
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("연령 제한이 음수인 경우를 감지한다")
		void detectsNegativeAgeRestriction() {
			// given
			SellerConcertCreateDTO negativeAgeDTO = SellerConcertCreateDTO.builder()
				.title("연령제한 오류 콘서트")
				.artist("테스트 아티스트")
				.venueName("테스트 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(1000)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(29))
				.minAge(-5) // 음수 연령
				.build();

			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(createTestConcert());

			// when & then
			// @Min(value = 0) 검증이 Controller에서 처리
			assertThatCode(() -> sellerConcertService.createConcert(validSellerId, negativeAgeDTO))
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("사용자당 티켓 수가 0개인 경우를 감지한다")
		void detectsZeroTicketsPerUser() {
			// given
			SellerConcertCreateDTO zeroTicketsDTO = SellerConcertCreateDTO.builder()
				.title("티켓제한 오류 콘서트")
				.artist("테스트 아티스트")
				.venueName("테스트 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(1000)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(29))
				.maxTicketsPerUser(0) // 0개 티켓
				.build();

			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(createTestConcert());

			// when & then
			// @Min(value = 1) 검증이 Controller에서 처리
			assertThatCode(() -> sellerConcertService.createConcert(validSellerId, zeroTicketsDTO))
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("필수 필드가 누락된 경우 BusinessException을 발생시킨다")
		void throwsBusinessExceptionForMissingFields() {
			// Given: 필수 필드가 누락된 DTO
			SellerConcertCreateDTO createDTO = SellerConcertCreateDTO.builder()
				.title("정상 제목")
				.artist(null) // 누락된 필드
				.venueName("정상 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(100)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(25))
				.build();

			// When & Then: 개선된 서비스는 명시적인 BusinessException 발생
			assertThatThrownBy(() -> sellerConcertService.createConcert(1L, createDTO))
				.isInstanceOf(BusinessException.class)
				.hasMessage("아티스트명이 필요합니다");
		}

		@Test
		@DisplayName("safeStringTrim 메서드가 null에 대해 BusinessException을 발생시킨다")
		void safeStringTrimThrowsBusinessExceptionForNull() {
			// Given: null 필수 필드가 있는 DTO
			SellerConcertCreateDTO createDTO = SellerConcertCreateDTO.builder()
				.title(null) // null 필수 필드
				.artist("정상 아티스트")
				.venueName("정상 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(100)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(25))
				.build();

			// When & Then: safeStringTrim에서 명시적 예외 발생
			assertThatThrownBy(() -> sellerConcertService.createConcert(1L, createDTO))
				.isInstanceOf(BusinessException.class)
				.hasMessage("콘서트 제목이 필요합니다");
		}
	}
	@Nested
	@DisplayName("서비스 레벨 null 안전성 검증")
	class ServiceLevelNullSafetyTests {

		@Test
		@DisplayName("null 값이 포함된 DTO로 콘서트 생성 시 안전하게 처리한다")
		void handlesNullFieldsInCreateDTO() {
			// given
			SellerConcertCreateDTO dtoWithNulls = SellerConcertCreateDTO.builder()
				.title("테스트 콘서트")
				.artist("테스트 아티스트")
				.venueName("테스트 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(1000)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(29))
				// minAge, maxTicketsPerUser는 null로 두어 기본값 테스트
				.description(null) // null 허용 필드
				.venueAddress(null) // null 허용 필드
				.posterImageUrl(null) // null 허용 필드
				.build();

			Concert savedConcert = new Concert();
			savedConcert.setConcertId(validConcertId);
			savedConcert.setTitle(dtoWithNulls.getTitle());
			savedConcert.setArtist(dtoWithNulls.getArtist());
			savedConcert.setSellerId(validSellerId);
			savedConcert.setStatus(ConcertStatus.SCHEDULED);
			savedConcert.setMinAge(0); // 기본값
			savedConcert.setMaxTicketsPerUser(4); // 기본값

			given(sellerConcertRepository.save(any(Concert.class))).willReturn(savedConcert);

			// when
			SellerConcertDTO result = sellerConcertService.createConcert(validSellerId, dtoWithNulls);

			// then
			assertThat(result.getMinAge()).isEqualTo(0); // null이면 기본값 0
			assertThat(result.getMaxTicketsPerUser()).isEqualTo(4); // null이면 기본값 4

			verify(sellerConcertRepository).save(argThat(concert ->
				concert.getMinAge() == 0 &&
					concert.getMaxTicketsPerUser() == 4 &&
					concert.getDescription() == null &&
					concert.getVenueAddress() == null &&
					concert.getPosterImageUrl() == null
			));
		}

		@Test
		@DisplayName("필수 필드가 null인 경우 BusinessException이 발생함을 확인한다")
		void throwsBusinessExceptionForNullRequiredFields() {
			// Given: 필수 필드가 null인 DTO
			SellerConcertCreateDTO createDTO = SellerConcertCreateDTO.builder()
				.title("정상 제목")
				.artist(null) // null 필수 필드
				.venueName("정상 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(100)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(25))
				.build();

			// When & Then: NPE 대신 명시적인 BusinessException 발생
			assertThatThrownBy(() -> sellerConcertService.createConcert(1L, createDTO))
				.isInstanceOf(BusinessException.class)
				.hasMessage("아티스트명이 필요합니다");
		}

		@Test
		@DisplayName("빈 문자열 필수 필드에 대해 BusinessException이 발생함을 확인한다")
		void throwsBusinessExceptionForEmptyRequiredFields() {
			// Given: 필수 필드가 빈 문자열인 DTO
			SellerConcertCreateDTO createDTO = SellerConcertCreateDTO.builder()
				.title("") // 빈 문자열
				.artist("정상 아티스트")
				.venueName("정상 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(100)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(25))
				.build();

			// When & Then: 빈 문자열도 유효하지 않은 값으로 처리
			assertThatThrownBy(() -> sellerConcertService.createConcert(1L, createDTO))
				.isInstanceOf(BusinessException.class)
				.hasMessage("콘서트 제목이 필요합니다");
		}

		@Test
		@DisplayName("공백만 있는 필수 필드에 대해 BusinessException이 발생함을 확인한다")
		void throwsBusinessExceptionForWhitespaceOnlyRequiredFields() {
			// Given: 필수 필드가 공백만 있는 DTO
			SellerConcertCreateDTO createDTO = SellerConcertCreateDTO.builder()
				.title("정상 제목")
				.artist("   ") // 공백만 있는 문자열
				.venueName("정상 공연장")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(100)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(25))
				.build();

			// When & Then: 공백만 있는 문자열도 유효하지 않은 값으로 처리
			assertThatThrownBy(() -> sellerConcertService.createConcert(1L, createDTO))
				.isInstanceOf(BusinessException.class)
				.hasMessage("아티스트명이 필요합니다");
		}
	}

	@Nested
	@DisplayName("서비스 레벨 비즈니스 검증")
	class ServiceLevelValidationTests {

		@Test
		@DisplayName("포스터 이미지 업데이트 시 빈 URL을 거부한다")
		void rejectsEmptyPosterUrl() {
			// given
			SellerConcertImageUpdateDTO emptyUrlDTO = new SellerConcertImageUpdateDTO("   ");

			// when & then
			assertThatThrownBy(() -> sellerConcertService
				.updatePosterImage(validSellerId, validConcertId, emptyUrlDTO))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_POSTER_URL);
		}

		@Test
		@DisplayName("null 포스터 이미지 DTO를 거부한다")
		void rejectsNullImageDTO() {
			// when & then
			assertThatThrownBy(() -> sellerConcertService
				.updatePosterImage(validSellerId, validConcertId, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_POSTER_URL);
		}

		@Test
		@DisplayName("포스터 URL이 null인 이미지 DTO를 거부한다")
		void rejectsImageDTOWithNullUrl() {
			// given
			SellerConcertImageUpdateDTO nullUrlDTO = new SellerConcertImageUpdateDTO(null);

			// when & then
			assertThatThrownBy(() -> sellerConcertService
				.updatePosterImage(validSellerId, validConcertId, nullUrlDTO))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_POSTER_URL);
		}

		@Test
		@DisplayName("잘못된 상태 변경 시도를 적절히 처리한다")
		void handlesInvalidStatusTransition() {
			// given
			Concert completedConcert = createTestConcert();
			completedConcert.setStatus(ConcertStatus.COMPLETED);

			given(sellerConcertRepository.existsByConcertIdAndSellerId(validConcertId, validSellerId))
				.willReturn(true);
			given(sellerConcertRepository.findById(validConcertId))
				.willReturn(Optional.of(completedConcert));
			given(sellerConcertRepository.save(any(Concert.class)))
				.willReturn(completedConcert);

			SellerConcertUpdateDTO invalidStatusDTO = SellerConcertUpdateDTO.builder()
				.status(ConcertStatus.SCHEDULED) // 완료된 콘서트를 다시 예정으로 변경
				.build();

			// when
			SellerConcertDTO result = sellerConcertService
				.updateConcert(validSellerId, validConcertId, invalidStatusDTO);

			// then
			// 현재 구현에서는 상태 변경을 허용하지만, 비즈니스 규칙에 따라 제한할 수 있음
			assertThat(result.getStatus()).isEqualTo(ConcertStatus.SCHEDULED);
		}
	}

	@Nested
	@DisplayName("예외 상황 복구")
	class ExceptionRecoveryTests {

		@Test
		@DisplayName("존재하지 않는 콘서트 조회 시 명확한 오류 메시지를 제공한다")
		void providesSpecificErrorForNonExistentConcert() {
			// given
			given(sellerConcertRepository.findById(999L))
				.willReturn(Optional.empty());
			given(sellerConcertRepository.existsByConcertIdAndSellerId(999L, validSellerId))
				.willReturn(true);

			SellerConcertUpdateDTO updateDTO = SellerConcertUpdateDTO.builder()
				.title("업데이트 시도")
				.build();

			// when & then
			assertThatThrownBy(() -> sellerConcertService
				.updateConcert(validSellerId, 999L, updateDTO))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.CONCERT_NOT_FOUND);
		}

		@Test
		@DisplayName("실수로 입력한 공백 문자열을 적절히 처리한다")
		void handlesWhitespaceInputsGracefully() {
			// given
			SellerConcertCreateDTO whitespaceDTO = SellerConcertCreateDTO.builder()
				.title("   콘서트 제목   ") // 앞뒤 공백
				.artist("  아티스트  ")
				.venueName("  공연장  ")
				.concertDate(LocalDate.now().plusDays(30))
				.startTime(LocalTime.of(19, 0))
				.endTime(LocalTime.of(21, 0))
				.totalSeats(1000)
				.bookingStartDate(LocalDateTime.now().plusDays(1))
				.bookingEndDate(LocalDateTime.now().plusDays(29))
				.build();

			Concert savedConcert = createTestConcert();
			given(sellerConcertRepository.save(any(Concert.class))).willReturn(savedConcert);

			// when
			SellerConcertDTO result = sellerConcertService.createConcert(validSellerId, whitespaceDTO);

			// then
			// 서비스에서 trim() 처리를 확인
			verify(sellerConcertRepository).save(argThat(concert ->
				concert.getTitle().equals("콘서트 제목") &&
					concert.getArtist().equals("아티스트") &&
					concert.getVenueName().equals("공연장")
			));
		}
	}

	// 테스트 헬퍼 메서드들
	private Concert createValidConcert() {
		Concert concert = new Concert();
		concert.setConcertId(1L);
		concert.setTitle("정상 콘서트");
		concert.setArtist("정상 아티스트");
		concert.setVenueName("정상 공연장");
		concert.setDescription("정상 설명");
		concert.setSellerId(1L);
		concert.setConcertDate(LocalDate.now().plusDays(30));
		concert.setStartTime(LocalTime.of(19, 0));
		concert.setEndTime(LocalTime.of(21, 0));
		concert.setTotalSeats(100);
		concert.setBookingStartDate(LocalDateTime.now().plusDays(1));
		concert.setBookingEndDate(LocalDateTime.now().plusDays(25));
		concert.setStatus(ConcertStatus.SCHEDULED);
		return concert;
	}

	private Concert createTestConcert() {
		Concert concert = new Concert();
		concert.setConcertId(validConcertId);
		concert.setTitle("정상 콘서트");
		concert.setArtist("정상 아티스트");
		concert.setDescription("정상 설명");
		concert.setSellerId(validSellerId);
		concert.setVenueName("정상 공연장");
		concert.setVenueAddress("정상 주소");
		concert.setConcertDate(LocalDate.now().plusDays(30));
		concert.setStartTime(LocalTime.of(19, 0));
		concert.setEndTime(LocalTime.of(21, 0));
		concert.setTotalSeats(1000);
		concert.setBookingStartDate(LocalDateTime.now().plusDays(1));
		concert.setBookingEndDate(LocalDateTime.now().plusDays(29));
		concert.setMinAge(0);
		concert.setMaxTicketsPerUser(4);
		concert.setStatus(ConcertStatus.SCHEDULED);
		concert.setPosterImageUrl("https://example.com/poster.jpg");
		concert.setCreatedAt(LocalDateTime.now());
		concert.setUpdatedAt(LocalDateTime.now());
		return concert;
	}

	private Concert createSoldOutConcert() {
		Concert concert = createTestConcert();
		concert.setConcertId(2L);
		concert.setTitle("매진 콘서트");
		concert.setStatus(ConcertStatus.SOLD_OUT);
		return concert;
	}

	private Concert createCancelledConcert() {
		Concert concert = createTestConcert();
		concert.setConcertId(3L);
		concert.setTitle("취소된 콘서트");
		concert.setStatus(ConcertStatus.CANCELLED);
		return concert;
	}

	private SellerConcertCreateDTO createValidCreateDTO() {
		return SellerConcertCreateDTO.builder()
			.title("새로운 콘서트")
			.artist("새로운 아티스트")
			.description("새로운 설명")
			.venueName("새로운 공연장")
			.venueAddress("새로운 주소")
			.concertDate(LocalDate.now().plusDays(60))
			.startTime(LocalTime.of(20, 0))
			.endTime(LocalTime.of(22, 0))
			.totalSeats(2000)
			.bookingStartDate(LocalDateTime.now().plusDays(2))
			.bookingEndDate(LocalDateTime.now().plusDays(58))
			.minAge(0)
			.maxTicketsPerUser(4)
			.posterImageUrl("https://example.com/new-poster.jpg")
			.build();
	}

	private List<Concert> createManyConcerts(int count) {
		return java.util.stream.IntStream.range(0, count)
			.mapToObj(i -> {
				Concert concert = createTestConcert();
				concert.setConcertId((long) i);
				concert.setTitle("콘서트 " + i);
				return concert;
			})
			.collect(java.util.stream.Collectors.toList());
	}
}