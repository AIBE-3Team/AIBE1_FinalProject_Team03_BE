package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.Review;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ReviewRepository;
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
@DisplayName("ReviewService 단위 테스트")
class ReviewServiceTest {

	@Mock
	private ReviewRepository reviewRepository;

	@Mock
	private ConcertRepository concertRepository;

	@InjectMocks
	private ReviewService reviewService;

	private Concert testConcert;
	private Review testReview;
	private ReviewDTO testReviewDTO;

	@BeforeEach
	void setUp() {
		testConcert = createTestConcert();
		testReview = createTestReview();
		testReviewDTO = createTestReviewDTO();
	}

	@Nested
	@DisplayName("후기 조회 테스트")
	class GetConcertReviewsTest {

		@Test
		@DisplayName("정상적인 페이징으로 후기 목록 조회 성공")
		void getConcertReviews_WithValidParams_ShouldReturnPagedReviews() {
			// given
			Long concertId = 1L;
			Pageable pageable = PageRequest.of(0, 10);
			Page<Review> reviewPage = new PageImpl<>(Arrays.asList(testReview), pageable, 1);

			given(reviewRepository.findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable))
				.willReturn(reviewPage);

			// when
			Page<ReviewDTO> result = reviewService.getConcertReviews(concertId, pageable);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getContent()).hasSize(1);
			assertThat(result.getContent().get(0).getTitle()).isEqualTo("훌륭한 공연이었습니다");
			assertThat(result.getContent().get(0).getRating()).isEqualTo(5);
			verify(reviewRepository).findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable);
		}

		@Test
		@DisplayName("후기가 없는 콘서트 조회 시 빈 페이지 반환")
		void getConcertReviews_WithNoReviews_ShouldReturnEmptyPage() {
			// given
			Long concertId = 1L;
			Pageable pageable = PageRequest.of(0, 10);
			Page<Review> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

			given(reviewRepository.findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable))
				.willReturn(emptyPage);

			// when
			Page<ReviewDTO> result = reviewService.getConcertReviews(concertId, pageable);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getContent()).isEmpty();
			assertThat(result.getTotalElements()).isEqualTo(0);
			verify(reviewRepository).findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable);
		}
	}

	@Nested
	@DisplayName("후기 작성 테스트")
	class CreateReviewTest {

		@Test
		@DisplayName("유효한 후기 작성 성공")
		void createReview_WithValidDTO_ShouldReturnCreatedReview() {
			// given
			given(concertRepository.findById(testReviewDTO.getConcertId()))
				.willReturn(Optional.of(testConcert));
			given(reviewRepository.save(any(Review.class)))
				.willReturn(testReview);

			// when
			ReviewDTO result = reviewService.createReview(testReviewDTO);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getTitle()).isEqualTo("훌륭한 공연이었습니다");
			assertThat(result.getRating()).isEqualTo(5);
			assertThat(result.getConcertId()).isEqualTo(1L);
			verify(concertRepository).findById(testReviewDTO.getConcertId());
			verify(reviewRepository).save(any(Review.class));
		}

		@Test
		@DisplayName("존재하지 않는 콘서트에 후기 작성 시 예외 발생")
		void createReview_WithNonExistentConcert_ShouldThrowException() {
			// given
			given(concertRepository.findById(testReviewDTO.getConcertId()))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> reviewService.createReview(testReviewDTO))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.CONCERT_NOT_FOUND);

			verify(concertRepository).findById(testReviewDTO.getConcertId());
			verify(reviewRepository, never()).save(any(Review.class));
		}

		@Test
		@DisplayName("Review 엔티티 필드가 정확히 설정되는지 확인")
		void createReview_ShouldSetAllFieldsCorrectly() {
			// given
			given(concertRepository.findById(testReviewDTO.getConcertId()))
				.willReturn(Optional.of(testConcert));
			given(reviewRepository.save(any(Review.class)))
				.willAnswer(invocation -> {
					Review review = invocation.getArgument(0);
					review.setId(1L); // ID 설정
					return review;
				});

			// when
			ReviewDTO result = reviewService.createReview(testReviewDTO);

			// then
			verify(reviewRepository).save(argThat(review -> {
				assertThat(review.getConcert()).isEqualTo(testConcert);
				assertThat(review.getUserId()).isEqualTo(testReviewDTO.getUserId());
				assertThat(review.getUserNickname()).isEqualTo(testReviewDTO.getUserNickname());
				assertThat(review.getTitle()).isEqualTo(testReviewDTO.getTitle());
				assertThat(review.getDescription()).isEqualTo(testReviewDTO.getDescription());
				assertThat(review.getRating()).isEqualTo(testReviewDTO.getRating());
				return true;
			}));
		}
	}

	@Nested
	@DisplayName("후기 수정 테스트")
	class UpdateReviewTest {

		@Test
		@DisplayName("유효한 후기 수정 성공")
		void updateReview_WithValidParams_ShouldReturnUpdatedReview() {
			// given
			Long reviewId = 1L;
			Long concertId = 1L;
			ReviewDTO updateDTO = new ReviewDTO();
			updateDTO.setTitle("수정된 제목");
			updateDTO.setDescription("수정된 내용");

			Review updatedReview = new Review();
			updatedReview.setId(reviewId);
			updatedReview.setConcert(testConcert);
			updatedReview.setUserId(100L);
			updatedReview.setUserNickname("testUser");
			updatedReview.setTitle("수정된 제목");
			updatedReview.setDescription("수정된 내용");
			updatedReview.setRating(4);

			given(reviewRepository.findByIdAndConcertConcertId(reviewId, concertId))
				.willReturn(Optional.of(testReview));
			given(reviewRepository.save(any(Review.class)))
				.willReturn(updatedReview);

			// when
			Optional<ReviewDTO> result = reviewService.updateReview(reviewId, concertId, updateDTO);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().getTitle()).isEqualTo("수정된 제목");
			assertThat(result.get().getDescription()).isEqualTo("수정된 내용");
			verify(reviewRepository).findByIdAndConcertConcertId(reviewId, concertId);
			verify(reviewRepository).save(any(Review.class));
		}

		@Test
		@DisplayName("존재하지 않는 후기 수정 시 빈 Optional 반환")
		void updateReview_WithNonExistentReview_ShouldReturnEmpty() {
			// given
			Long reviewId = 999L;
			Long concertId = 1L;
			ReviewDTO updateDTO = new ReviewDTO();
			updateDTO.setTitle("수정된 제목");
			updateDTO.setDescription("수정된 내용");

			given(reviewRepository.findByIdAndConcertConcertId(reviewId, concertId))
				.willReturn(Optional.empty());

			// when
			Optional<ReviewDTO> result = reviewService.updateReview(reviewId, concertId, updateDTO);

			// then
			assertThat(result).isEmpty();
			verify(reviewRepository).findByIdAndConcertConcertId(reviewId, concertId);
			verify(reviewRepository, never()).save(any(Review.class));
		}

		@Test
		@DisplayName("다른 콘서트의 후기 수정 시도 시 빈 Optional 반환")
		void updateReview_WithDifferentConcertId_ShouldReturnEmpty() {
			// given
			Long reviewId = 1L;
			Long wrongConcertId = 999L;
			ReviewDTO updateDTO = new ReviewDTO();

			given(reviewRepository.findByIdAndConcertConcertId(reviewId, wrongConcertId))
				.willReturn(Optional.empty());

			// when
			Optional<ReviewDTO> result = reviewService.updateReview(reviewId, wrongConcertId, updateDTO);

			// then
			assertThat(result).isEmpty();
			verify(reviewRepository).findByIdAndConcertConcertId(reviewId, wrongConcertId);
		}
	}

	@Nested
	@DisplayName("후기 삭제 테스트")
	class DeleteReviewTest {

		@Test
		@DisplayName("유효한 후기 삭제 성공")
		void deleteReview_WithValidParams_ShouldReturnTrue() {
			// given
			Long reviewId = 1L;
			Long concertId = 1L;

			given(reviewRepository.findByIdAndConcertConcertId(reviewId, concertId))
				.willReturn(Optional.of(testReview));

			// when
			boolean result = reviewService.deleteReview(reviewId, concertId);

			// then
			assertThat(result).isTrue();
			verify(reviewRepository).findByIdAndConcertConcertId(reviewId, concertId);
			verify(reviewRepository).delete(testReview);
		}

		@Test
		@DisplayName("존재하지 않는 후기 삭제 시 false 반환")
		void deleteReview_WithNonExistentReview_ShouldReturnFalse() {
			// given
			Long reviewId = 999L;
			Long concertId = 1L;

			given(reviewRepository.findByIdAndConcertConcertId(reviewId, concertId))
				.willReturn(Optional.empty());

			// when
			boolean result = reviewService.deleteReview(reviewId, concertId);

			// then
			assertThat(result).isFalse();
			verify(reviewRepository).findByIdAndConcertConcertId(reviewId, concertId);
			verify(reviewRepository, never()).delete(any(Review.class));
		}

		@Test
		@DisplayName("다른 콘서트의 후기 삭제 시도 시 false 반환")
		void deleteReview_WithDifferentConcertId_ShouldReturnFalse() {
			// given
			Long reviewId = 1L;
			Long wrongConcertId = 999L;

			given(reviewRepository.findByIdAndConcertConcertId(reviewId, wrongConcertId))
				.willReturn(Optional.empty());

			// when
			boolean result = reviewService.deleteReview(reviewId, wrongConcertId);

			// then
			assertThat(result).isFalse();
			verify(reviewRepository).findByIdAndConcertConcertId(reviewId, wrongConcertId);
			verify(reviewRepository, never()).delete(any(Review.class));
		}
	}

	@Nested
	@DisplayName("DTO 변환 테스트")
	class ConvertToDTOTest {

		@Test
		@DisplayName("Review 엔티티가 DTO로 올바르게 변환되는지 확인")
		void convertToDTO_ShouldMapAllFieldsCorrectly() {
			// given
			given(concertRepository.findById(testReviewDTO.getConcertId()))
				.willReturn(Optional.of(testConcert));
			given(reviewRepository.save(any(Review.class)))
				.willReturn(testReview);

			// when
			ReviewDTO result = reviewService.createReview(testReviewDTO);

			// then
			assertThat(result.getId()).isEqualTo(testReview.getId());
			assertThat(result.getConcertId()).isEqualTo(testReview.getConcert().getConcertId());
			assertThat(result.getUserId()).isEqualTo(testReview.getUserId());
			assertThat(result.getUserNickname()).isEqualTo(testReview.getUserNickname());
			assertThat(result.getTitle()).isEqualTo(testReview.getTitle());
			assertThat(result.getDescription()).isEqualTo(testReview.getDescription());
			assertThat(result.getRating()).isEqualTo(testReview.getRating());
			assertThat(result.getCreatedAt()).isEqualTo(testReview.getCreatedAt());
			assertThat(result.getUpdatedAt()).isEqualTo(testReview.getUpdatedAt());
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

	private Review createTestReview() {
		Review review = new Review();
		review.setId(1L);
		review.setConcert(testConcert);
		review.setUserId(100L);
		review.setUserNickname("testUser");
		review.setTitle("훌륭한 공연이었습니다");
		review.setDescription("정말 감동적인 공연이었습니다. 다시 보고 싶어요.");
		review.setRating(5);
		// BaseTimeEntity 필드들은 실제로는 JPA가 자동으로 설정하지만, 테스트에서는 수동으로 설정
		review.setCreatedAt(LocalDateTime.of(2024, 6, 21, 10, 0));
		review.setUpdatedAt(LocalDateTime.of(2024, 6, 21, 10, 0));
		return review;
	}

	private ReviewDTO createTestReviewDTO() {
		return new ReviewDTO(
			null, // ID는 생성 시에는 null
			1L,   // concertId
			100L, // userId
			"testUser",
			"훌륭한 공연이었습니다",
			"정말 감동적인 공연이었습니다. 다시 보고 싶어요.",
			5,
			null, // createdAt은 생성 시에는 null
			null  // updatedAt은 생성 시에는 null
		);
	}
}