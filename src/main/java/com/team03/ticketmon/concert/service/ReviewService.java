package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.Review;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ReviewRepository;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/*
 * Review Service
 * 후기 비즈니스 로직 처리
 */

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final ConcertRepository concertRepository;

	/**
	 * 콘서트 후기 조회 (페이징 지원으로 수정)
	 */
	@Transactional(readOnly = true)
	public Page<ReviewDTO> getConcertReviews(Long concertId, Pageable pageable) {
		return reviewRepository.findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable)
			.map(this::convertToDTO);
	}

	/**
	 * 후기 작성
	 */
	public ReviewDTO createReview(ReviewDTO reviewDTO) {
		Concert concert = concertRepository.findById(reviewDTO.getConcertId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

		Review review = new Review();
		review.setConcert(concert);
		review.setUserId(reviewDTO.getUserId());
		review.setUserNickname(reviewDTO.getUserNickname());
		review.setTitle(reviewDTO.getTitle());
		review.setDescription(reviewDTO.getDescription());
		review.setRating(reviewDTO.getRating());

		review = reviewRepository.save(review);
		return convertToDTO(review);
	}

	/**
	 * 후기 수정
	 */
	public Optional<ReviewDTO> updateReview(Long reviewId, Long concertId, ReviewDTO reviewDTO) {
		return reviewRepository.findByIdAndConcertConcertId(reviewId, concertId)
			.map(review -> {
				review.setTitle(reviewDTO.getTitle());
				review.setDescription(reviewDTO.getDescription());
				return convertToDTO(reviewRepository.save(review));
			});
	}

	/**
	 * 후기 삭제
	 */
	public boolean deleteReview(Long reviewId, Long concertId) {
		Optional<Review> review = reviewRepository.findByIdAndConcertConcertId(reviewId, concertId);
		if (review.isPresent()) {
			reviewRepository.delete(review.get());
			return true;
		}
		return false;
	}

	/**
	 * Review Entity를 DTO로 변환
	 */
	private ReviewDTO convertToDTO(Review review) {
		return new ReviewDTO(
			review.getId(),
			review.getConcert().getConcertId(),
			review.getUserId(),
			review.getUserNickname(),
			review.getTitle(),
			review.getDescription(),
			review.getRating(),
			review.getCreatedAt(),
			review.getUpdatedAt()
		);
	}
}

