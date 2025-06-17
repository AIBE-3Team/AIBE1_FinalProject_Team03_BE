package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.ExpectationReviewDTO;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ExpectationReview;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ExpectationReviewRepository;
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
	public List<ExpectationReviewDTO> getConcertExpectationReviews(Long concertId) {
		return expectationReviewRepository.findByConcertConcertIdOrderByCreatedAtDesc(concertId)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
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
		review.setCreatedAt(LocalDateTime.now());
		review.setUpdatedAt(LocalDateTime.now());

		review = expectationReviewRepository.save(review);
		return convertToDTO(review);
	}

	/**
	 * 기대평 수정
	 */
	public Optional<ExpectationReviewDTO> updateExpectationReview(Long reviewId, ExpectationReviewDTO reviewDTO) {
		return expectationReviewRepository.findById(reviewId)
			.map(review -> {
				review.setComment(reviewDTO.getComment());
				review.setExpectationRating(reviewDTO.getExpectationRating());
				review.setUpdatedAt(LocalDateTime.now());
				return convertToDTO(expectationReviewRepository.save(review));
			});
	}

	/**
	 * 기대평 삭제
	 */
	public boolean deleteExpectationReview(Long reviewId) {
		if (expectationReviewRepository.existsById(reviewId)) {
			expectationReviewRepository.deleteById(reviewId);
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
