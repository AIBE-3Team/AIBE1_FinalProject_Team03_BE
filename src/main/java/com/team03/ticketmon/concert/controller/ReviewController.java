package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.service.ReviewService;
import com.team03.ticketmon._global.exception.SuccessResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * Review Controller
 * 콘서트 후기 관련 HTTP 요청 처리
 */

@RestController
@RequestMapping("/api/concerts/{concertId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;

	/**
	 * 후기 작성
	 */
	@PostMapping
	public ResponseEntity<SuccessResponse<ReviewDTO>> createReview(
		@PathVariable Long concertId,
	    @RequestBody @Valid ReviewDTO reviewDTO) {
	    // concertId 일치 여부 검증
	    if (reviewDTO.getConcertId() != null && !reviewDTO.getConcertId().equals(concertId)) {
	        throw new BusinessException(ErrorCode.INVALID_INPUT);
	    }
		reviewDTO.setConcertId(concertId);
		ReviewDTO createdReview = reviewService.createReview(reviewDTO);
		return ResponseEntity.ok(SuccessResponse.of("후기가 작성되었습니다.", createdReview));
	}

	/**
	 * 후기 수정
	 */
	@PutMapping("/{reviewId}")
	public ResponseEntity<SuccessResponse<ReviewDTO>> updateReview(
		@PathVariable Long concertId,
		@PathVariable Long reviewId,
		@RequestBody @Valid ReviewDTO reviewDTO) {
		return reviewService.updateReview(reviewId, concertId, reviewDTO)
			.map(review -> ResponseEntity.ok(SuccessResponse.of("후기가 수정되었습니다.", review)))
			.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * 후기 삭제
	 */
	@DeleteMapping("/{reviewId}")
	public ResponseEntity<SuccessResponse<Void>> deleteReview(
		@PathVariable Long concertId,
		@PathVariable Long reviewId) {
		boolean deleted = reviewService.deleteReview(reviewId, concertId);
		return deleted ? ResponseEntity.ok(SuccessResponse.of("후기가 삭제되었습니다.", null)) : ResponseEntity.notFound().build();
	}
}
