package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ExpectationReview;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.dto.ExpectationReviewDTO;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ExpectationReviewRepository;
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
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpectationReviewService 단위 테스트")
class ExpectationReviewServiceTest {

	@Mock
	private ExpectationReviewRepository expectationReviewRepository;

	@Mock
	private ConcertRepository concertRepository;

	@InjectMocks
	private ExpectationReviewService expectationReviewService;

	private Concert testConcert;
	private ExpectationReview testExpectationReview;
	private ExpectationReviewDTO testExpectationReviewDTO;

	@BeforeEach
	void setUp() {
		testConcert = createTestConcert();
		testExpectationReview = createTestExpectationReview();
		testExpectationReviewDTO = createTestExpectationReviewDTO();
	}

	@Nested
	@DisplayName("기대평 조회 테스트")
	class GetConcertExpectationReviewsTest {

		@Test
		@DisplayName("정상적인 페이징으로 기대평 목록 조회 성공")
		void getConcertExpectationReviews_WithValidParams_ShouldReturnPagedExpectations() {
			// given
			Long concertId = 1L;
			Pageable pageable = PageRequest.of(0, 10);
			Page<ExpectationReview> expectationPage = new PageImpl<>(Arrays.asList(testExpectationReview), pageable, 1);

			given(expectationReviewRepository.findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable))
				.willReturn(expectationPage);

			// when
			Page<ExpectationReviewDTO> result = expectationReviewService.getConcertExpectationReviews(concertId, pageable);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getContent()).hasSize(1);
			assertThat(result.getContent().get(0).getComment()).isEqualTo("정말 기대되는 공연입니다!");
			assertThat(result.getContent().get(0).getExpectationRating()).isEqualTo(5);
			verify(expectationReviewRepository).findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable);
		}

		@Test
		@DisplayName("기대평이 없는 콘서트 조회 시 빈 페이지 반환")
		void getConcertExpectationReviews_WithNoExpectations_ShouldReturnEmptyPage() {
			// given
			Long concertId = 1L;
			Pageable pageable = PageRequest.of(0, 10);
			Page<ExpectationReview> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

			given(expectationReviewRepository.findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable))
				.willReturn(emptyPage);

			// when
			Page<ExpectationReviewDTO> result = expectationReviewService.getConcertExpectationReviews(concertId, pageable);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getContent()).isEmpty();
			assertThat(result.getTotalElements()).isEqualTo(0);
			verify(expectationReviewRepository).findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable);
		}

		@Test
		@DisplayName("여러 기대평이 있는 경우 최신순으로 정렬되어 조회")
		void getConcertExpectationReviews_WithMultipleExpectations_ShouldReturnSortedByCreatedAtDesc() {
			// given
			Long concertId = 1L;
			Pageable pageable = PageRequest.of(0, 10);

			ExpectationReview oldExpectation = createExpectationReview(1L, "첫 번째 기대평", 4,
				LocalDateTime.of(2024, 6, 20, 10, 0));
			ExpectationReview newExpectation = createExpectationReview(2L, "두 번째 기대평", 5,
				LocalDateTime.of(2024, 6, 21, 10, 0));

			Page<ExpectationReview> expectationPage = new PageImpl<>(
				Arrays.asList(newExpectation, oldExpectation), pageable, 2);

			given(expectationReviewRepository.findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable))
				.willReturn(expectationPage);

			// when
			Page<ExpectationReviewDTO> result = expectationReviewService.getConcertExpectationReviews(concertId, pageable);

			// then
			assertThat(result.getContent()).hasSize(2);
			assertThat(result.getContent().get(0).getComment()).isEqualTo("두 번째 기대평");
			assertThat(result.getContent().get(1).getComment()).isEqualTo("첫 번째 기대평");
		}
	}

	@Nested
	@DisplayName("기대평 작성 테스트")
	class CreateExpectationReviewTest {

		@Test
		@DisplayName("유효한 기대평 작성 성공")
		void createExpectationReview_WithValidDTO_ShouldReturnCreatedExpectation() {
			// given
			given(concertRepository.findById(testExpectationReviewDTO.getConcertId()))
				.willReturn(Optional.of(testConcert));
			given(expectationReviewRepository.save(any(ExpectationReview.class)))
				.willReturn(testExpectationReview);

			// when
			ExpectationReviewDTO result = expectationReviewService.createExpectationReview(testExpectationReviewDTO);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getComment()).isEqualTo("정말 기대되는 공연입니다!");
			assertThat(result.getExpectationRating()).isEqualTo(5);
			assertThat(result.getConcertId()).isEqualTo(1L);
			verify(concertRepository).findById(testExpectationReviewDTO.getConcertId());
			verify(expectationReviewRepository).save(any(ExpectationReview.class));
		}

		@Test
		@DisplayName("존재하지 않는 콘서트에 기대평 작성 시 예외 발생")
		void createExpectationReview_WithNonExistentConcert_ShouldThrowException() {
			// given
			given(concertRepository.findById(testExpectationReviewDTO.getConcertId()))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> expectationReviewService.createExpectationReview(testExpectationReviewDTO))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.CONCERT_NOT_FOUND);

			verify(concertRepository).findById(testExpectationReviewDTO.getConcertId());
			verify(expectationReviewRepository, never()).save(any(ExpectationReview.class));
		}

		@Test
		@DisplayName("ExpectationReview 엔티티 필드가 정확히 설정되는지 확인")
		void createExpectationReview_ShouldSetAllFieldsCorrectly() {
			// given
			given(concertRepository.findById(testExpectationReviewDTO.getConcertId()))
				.willReturn(Optional.of(testConcert));
			given(expectationReviewRepository.save(any(ExpectationReview.class)))
				.willAnswer(invocation -> {
					ExpectationReview review = invocation.getArgument(0);
					review.setId(1L); // ID 설정
					return review;
				});

			// when
			ExpectationReviewDTO result = expectationReviewService.createExpectationReview(testExpectationReviewDTO);

			// then
			verify(expectationReviewRepository).save(argThat(review -> {
				assertThat(review.getConcert()).isEqualTo(testConcert);
				assertThat(review.getUserId()).isEqualTo(testExpectationReviewDTO.getUserId());
				assertThat(review.getUserNickname()).isEqualTo(testExpectationReviewDTO.getUserNickname());
				assertThat(review.getComment()).isEqualTo(testExpectationReviewDTO.getComment());
				assertThat(review.getExpectationRating()).isEqualTo(testExpectationReviewDTO.getExpectationRating());
				return true;
			}));
		}

		@Test
		@DisplayName("다양한 기대 점수로 기대평 작성 테스트")
		void createExpectationReview_WithDifferentRatings_ShouldWork() {
			// given
			ExpectationReviewDTO lowRatingDTO = createTestExpectationReviewDTO();
			lowRatingDTO.setExpectationRating(1);
			lowRatingDTO.setComment("조금 걱정되는 공연이에요");

			given(concertRepository.findById(lowRatingDTO.getConcertId()))
				.willReturn(Optional.of(testConcert));
			given(expectationReviewRepository.save(any(ExpectationReview.class)))
				.willAnswer(invocation -> {
					ExpectationReview review = invocation.getArgument(0);
					review.setId(2L);
					return review;
				});

			// when
			ExpectationReviewDTO result = expectationReviewService.createExpectationReview(lowRatingDTO);

			// then
			assertThat(result.getExpectationRating()).isEqualTo(1);
			assertThat(result.getComment()).isEqualTo("조금 걱정되는 공연이에요");
		}
	}

	@Nested
	@DisplayName("기대평 수정 테스트")
	class UpdateExpectationReviewTest {

		@Test
		@DisplayName("유효한 기대평 수정 성공")
		void updateExpectationReview_WithValidParams_ShouldReturnUpdatedExpectation() {
			// given
			Long concertId = 1L;
			Long reviewId = 1L;
			ExpectationReviewDTO updateDTO = new ExpectationReviewDTO();
			updateDTO.setComment("수정된 기대평 내용");
			updateDTO.setExpectationRating(4);

			ExpectationReview updatedReview = new ExpectationReview();
			updatedReview.setId(reviewId);
			updatedReview.setConcert(testConcert);
			updatedReview.setUserId(100L);
			updatedReview.setUserNickname("testUser");
			updatedReview.setComment("수정된 기대평 내용");
			updatedReview.setExpectationRating(4);

			given(expectationReviewRepository.findByIdAndConcertId(reviewId, concertId))
				.willReturn(Optional.of(testExpectationReview));
			given(expectationReviewRepository.save(any(ExpectationReview.class)))
				.willReturn(updatedReview);

			// when
			Optional<ExpectationReviewDTO> result = expectationReviewService.updateExpectationReview(concertId, reviewId, updateDTO);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().getComment()).isEqualTo("수정된 기대평 내용");
			assertThat(result.get().getExpectationRating()).isEqualTo(4);
			verify(expectationReviewRepository).findByIdAndConcertId(reviewId, concertId);
			verify(expectationReviewRepository).save(any(ExpectationReview.class));
		}

		@Test
		@DisplayName("존재하지 않는 기대평 수정 시 빈 Optional 반환")
		void updateExpectationReview_WithNonExistentExpectation_ShouldReturnEmpty() {
			// given
			Long concertId = 1L;
			Long reviewId = 999L;
			ExpectationReviewDTO updateDTO = new ExpectationReviewDTO();
			updateDTO.setComment("수정된 내용");
			updateDTO.setExpectationRating(3);

			given(expectationReviewRepository.findByIdAndConcertId(reviewId, concertId))
				.willReturn(Optional.empty());

			// when
			Optional<ExpectationReviewDTO> result = expectationReviewService.updateExpectationReview(concertId, reviewId, updateDTO);

			// then
			assertThat(result).isEmpty();
			verify(expectationReviewRepository).findByIdAndConcertId(reviewId, concertId);
			verify(expectationReviewRepository, never()).save(any(ExpectationReview.class));
		}

		@Test
		@DisplayName("다른 콘서트의 기대평 수정 시도 시 빈 Optional 반환")
		void updateExpectationReview_WithDifferentConcertId_ShouldReturnEmpty() {
			// given
			Long wrongConcertId = 999L;
			Long reviewId = 1L;
			ExpectationReviewDTO updateDTO = new ExpectationReviewDTO();

			given(expectationReviewRepository.findByIdAndConcertId(reviewId, wrongConcertId))
				.willReturn(Optional.empty());

			// when
			Optional<ExpectationReviewDTO> result = expectationReviewService.updateExpectationReview(wrongConcertId, reviewId, updateDTO);

			// then
			assertThat(result).isEmpty();
			verify(expectationReviewRepository).findByIdAndConcertId(reviewId, wrongConcertId);
		}

		@Test
		@DisplayName("기대평 수정 시 특정 필드만 업데이트되는지 확인")
		void updateExpectationReview_ShouldUpdateOnlySpecificFields() {
			// given
			Long concertId = 1L;
			Long reviewId = 1L;
			ExpectationReviewDTO updateDTO = new ExpectationReviewDTO();
			updateDTO.setComment("완전히 새로운 기대평");
			updateDTO.setExpectationRating(1);

			given(expectationReviewRepository.findByIdAndConcertId(reviewId, concertId))
				.willReturn(Optional.of(testExpectationReview));
			given(expectationReviewRepository.save(any(ExpectationReview.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

			// when
			Optional<ExpectationReviewDTO> result = expectationReviewService.updateExpectationReview(concertId, reviewId, updateDTO);

			// then
			verify(expectationReviewRepository).save(argThat(review -> {
				// 수정되어야 하는 필드들
				assertThat(review.getComment()).isEqualTo("완전히 새로운 기대평");
				assertThat(review.getExpectationRating()).isEqualTo(1);

				// 변경되지 않아야 하는 필드들
				assertThat(review.getUserId()).isEqualTo(testExpectationReview.getUserId());
				assertThat(review.getUserNickname()).isEqualTo(testExpectationReview.getUserNickname());
				assertThat(review.getConcert()).isEqualTo(testExpectationReview.getConcert());
				return true;
			}));
		}
	}

	@Nested
	@DisplayName("기대평 삭제 테스트")
	class DeleteExpectationReviewTest {

		@Test
		@DisplayName("유효한 기대평 삭제 성공")
		void deleteExpectationReview_WithValidParams_ShouldReturnTrue() {
			// given
			Long concertId = 1L;
			Long reviewId = 1L;

			given(expectationReviewRepository.findByIdAndConcertId(reviewId, concertId))
				.willReturn(Optional.of(testExpectationReview));

			// when
			boolean result = expectationReviewService.deleteExpectationReview(concertId, reviewId);

			// then
			assertThat(result).isTrue();
			verify(expectationReviewRepository).findByIdAndConcertId(reviewId, concertId);
			verify(expectationReviewRepository).delete(testExpectationReview);
		}

		@Test
		@DisplayName("존재하지 않는 기대평 삭제 시 false 반환")
		void deleteExpectationReview_WithNonExistentExpectation_ShouldReturnFalse() {
			// given
			Long concertId = 1L;
			Long reviewId = 999L;

			given(expectationReviewRepository.findByIdAndConcertId(reviewId, concertId))
				.willReturn(Optional.empty());

			// when
			boolean result = expectationReviewService.deleteExpectationReview(concertId, reviewId);

			// then
			assertThat(result).isFalse();
			verify(expectationReviewRepository).findByIdAndConcertId(reviewId, concertId);
			verify(expectationReviewRepository, never()).delete(any(ExpectationReview.class));
		}

		@Test
		@DisplayName("다른 콘서트의 기대평 삭제 시도 시 false 반환")
		void deleteExpectationReview_WithDifferentConcertId_ShouldReturnFalse() {
			// given
			Long wrongConcertId = 999L;
			Long reviewId = 1L;

			given(expectationReviewRepository.findByIdAndConcertId(reviewId, wrongConcertId))
				.willReturn(Optional.empty());

			// when
			boolean result = expectationReviewService.deleteExpectationReview(wrongConcertId, reviewId);

			// then
			assertThat(result).isFalse();
			verify(expectationReviewRepository).findByIdAndConcertId(reviewId, wrongConcertId);
			verify(expectationReviewRepository, never()).delete(any(ExpectationReview.class));
		}
	}

	@Nested
	@DisplayName("DTO 변환 테스트")
	class ConvertToDTOTest {

		@Test
		@DisplayName("ExpectationReview 엔티티가 DTO로 올바르게 변환되는지 확인")
		void convertToDTO_ShouldMapAllFieldsCorrectly() {
			// given
			given(concertRepository.findById(testExpectationReviewDTO.getConcertId()))
				.willReturn(Optional.of(testConcert));
			given(expectationReviewRepository.save(any(ExpectationReview.class)))
				.willReturn(testExpectationReview);

			// when
			ExpectationReviewDTO result = expectationReviewService.createExpectationReview(testExpectationReviewDTO);

			// then
			assertThat(result.getId()).isEqualTo(testExpectationReview.getId());
			assertThat(result.getConcertId()).isEqualTo(testExpectationReview.getConcert().getConcertId());
			assertThat(result.getUserId()).isEqualTo(testExpectationReview.getUserId());
			assertThat(result.getUserNickname()).isEqualTo(testExpectationReview.getUserNickname());
			assertThat(result.getComment()).isEqualTo(testExpectationReview.getComment());
			assertThat(result.getExpectationRating()).isEqualTo(testExpectationReview.getExpectationRating());
			assertThat(result.getCreatedAt()).isEqualTo(testExpectationReview.getCreatedAt());
			assertThat(result.getUpdatedAt()).isEqualTo(testExpectationReview.getUpdatedAt());
		}

		@Test
		@DisplayName("다양한 기대 점수의 기대평 DTO 변환 확인")
		void convertToDTO_WithDifferentRatings_ShouldMapCorrectly() {
			// given
			ExpectationReview lowRatingReview = createExpectationReview(2L, "별로 기대 안 됨", 2, LocalDateTime.now());
			given(concertRepository.findById(1L)).willReturn(Optional.of(testConcert));
			given(expectationReviewRepository.save(any(ExpectationReview.class))).willReturn(lowRatingReview);

			ExpectationReviewDTO inputDTO = new ExpectationReviewDTO(null, 1L, 200L, "user2", "별로 기대 안 됨", 2, null, null);

			// when
			ExpectationReviewDTO result = expectationReviewService.createExpectationReview(inputDTO);

			// then
			assertThat(result.getExpectationRating()).isEqualTo(2);
			assertThat(result.getComment()).isEqualTo("별로 기대 안 됨");
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

	private ExpectationReview createTestExpectationReview() {
		ExpectationReview review = new ExpectationReview();
		review.setId(1L);
		review.setConcert(testConcert);
		review.setUserId(100L);
		review.setUserNickname("testUser");
		review.setComment("정말 기대되는 공연입니다!");
		review.setExpectationRating(5);
		review.setCreatedAt(LocalDateTime.of(2024, 6, 21, 10, 0));
		review.setUpdatedAt(LocalDateTime.of(2024, 6, 21, 10, 0));
		return review;
	}

	private ExpectationReview createExpectationReview(Long id, String comment, Integer rating, LocalDateTime createdAt) {
		ExpectationReview review = new ExpectationReview();
		review.setId(id);
		review.setConcert(testConcert);
		review.setUserId(100L);
		review.setUserNickname("testUser");
		review.setComment(comment);
		review.setExpectationRating(rating);
		review.setCreatedAt(createdAt);
		review.setUpdatedAt(createdAt);
		return review;
	}

	private ExpectationReviewDTO createTestExpectationReviewDTO() {
		return new ExpectationReviewDTO(
			null, // ID는 생성 시에는 null
			1L,   // concertId
			100L, // userId
			"testUser",
			"정말 기대되는 공연입니다!",
			5,
			null, // createdAt은 생성 시에는 null
			null  // updatedAt은 생성 시에는 null
		);
	}
}