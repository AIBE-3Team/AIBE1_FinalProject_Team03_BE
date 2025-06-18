package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.ExpectationReviewDTO;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ExpectationReview;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ExpectationReviewRepository;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;
import java.util.Optional;

/*
 * Expectation Review Service
 * 기대평 비즈니스 로직 처리
 */

@Service
@RequiredArgsConstructor
@Transactional
public class ExpectationReviewService {

	private final ExpectationReviewRepository expectationReviewRepository;
	private final ConcertRepository concertRepository;

	/**
	 * 콘서트 기대평 조회
	 */
	@Transactional(readOnly = true)
	public Page<ExpectationReviewDTO> getConcertExpectationReviews(Long concertId, Pageable pageable) {
		return expectationReviewRepository.findByConcertConcertIdOrderByCreatedAtDesc(concertId, pageable)
			.map(this::convertToDTO);
	}

	/**
	 * 기대평 작성
	 */
	public ExpectationReviewDTO createExpectationReview(ExpectationReviewDTO reviewDTO) {
		Concert concert = concertRepository.findById(reviewDTO.getConcertId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

		ExpectationReview review = new ExpectationReview();
		review.setConcert(concert);
		review.setUserId(reviewDTO.getUserId());
		review.setUserNickname(reviewDTO.getUserNickname());
		review.setComment(reviewDTO.getComment());
		review.setExpectationRating(reviewDTO.getExpectationRating());

		review = expectationReviewRepository.save(review);
		return convertToDTO(review);
	}

	/**
	 * 기대평 수정
	 */
	public Optional<ExpectationReviewDTO> updateExpectationReview(Long concertId, Long reviewId, ExpectationReviewDTO reviewDTO) {
		return expectationReviewRepository.findByIdAndConcertId(reviewId, concertId)
			.map(review -> {
				review.setComment(reviewDTO.getComment());
				review.setExpectationRating(reviewDTO.getExpectationRating());
				return convertToDTO(expectationReviewRepository.save(review));
			});
	}

	/**
	 * 기대평 삭제
	 */
	public boolean deleteExpectationReview(Long concertId, Long reviewId) {
		Optional<ExpectationReview> review = expectationReviewRepository.findByIdAndConcertId(reviewId, concertId);
		if (review.isPresent()) {
			expectationReviewRepository.delete(review.get());
			return true;
		}
		return false;
	}

	/**
	 * ExpectationReview Entity를 DTO로 변환
	 */
	private ExpectationReviewDTO convertToDTO(ExpectationReview review) {
		return new ExpectationReviewDTO(
			review.getId(),
			review.getConcert().getConcertId(),
			review.getUserId(),
			review.getUserNickname(),
			review.getComment(),
			review.getExpectationRating(),
			review.getCreatedAt(),
			review.getUpdatedAt()
		);
	}
}
