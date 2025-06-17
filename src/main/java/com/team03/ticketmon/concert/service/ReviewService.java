package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.Review;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ReviewRepository;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
	 * 콘서트 후기 조회
	 */
	@Transactional(readOnly = true)
	public List<ReviewDTO> getConcertReviews(Long concertId) {
		return reviewRepository.findByConcertConcertIdOrderByCreatedAtDesc(concertId)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
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
		review.setCreatedAt(LocalDateTime.now());
		review.setUpdatedAt(LocalDateTime.now());

		review = reviewRepository.save(review);
		return convertToDTO(review);
	}

	/**
	 * 후기 수정
	 */
	public Optional<ReviewDTO> updateReview(Long reviewId, ReviewDTO reviewDTO) {
		return reviewRepository.findById(reviewId)
			.map(review -> {
				review.setTitle(reviewDTO.getTitle());
				review.setDescription(reviewDTO.getDescription());
				review.setUpdatedAt(LocalDateTime.now());
				return convertToDTO(reviewRepository.save(review));
			});
	}

	/**
	 * 후기 삭제
	 */
	public boolean deleteReview(Long reviewId) {
		if (reviewRepository.existsById(reviewId)) {
			reviewRepository.deleteById(reviewId);
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

