package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.service.ReviewService;
import com.team03.ticketmon._global.exception.SuccessResponse;
import lombok.RequiredArgsConstructor;
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
		@RequestBody ReviewDTO reviewDTO) {
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
		@RequestBody ReviewDTO reviewDTO) {
		return reviewService.updateReview(reviewId, reviewDTO)
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
		boolean deleted = reviewService.deleteReview(reviewId);
		return deleted ? ResponseEntity.ok(SuccessResponse.of("후기가 삭제되었습니다.", null)) : ResponseEntity.notFound().build();
	}
}
