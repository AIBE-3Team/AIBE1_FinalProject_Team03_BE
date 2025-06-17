package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon.concert.dto.ExpectationReviewDTO;
import com.team03.ticketmon.concert.service.ExpectationReviewService;
import com.team03.ticketmon._global.exception.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/*
 * Expectation Review Controller
 * 기대평 관련 HTTP 요청 처리
 */

@RestController
@RequestMapping("/api/concerts/{concertId}/expectations")
@RequiredArgsConstructor
public class ExpectationReviewController {

	private final ExpectationReviewService expectationReviewService;

	/**
	 * 콘서트 기대평 목록 조회
	 */
	@GetMapping
	public ResponseEntity<SuccessResponse<List<ExpectationReviewDTO>>> getConcertExpectationReviews(@PathVariable Long concertId) {
		List<ExpectationReviewDTO> expectations = expectationReviewService.getConcertExpectationReviews(concertId);
		return ResponseEntity.ok(SuccessResponse.of(expectations));
	}

	/**
	 * 기대평 작성
	 */
	@PostMapping
	public ResponseEntity<SuccessResponse<ExpectationReviewDTO>> createExpectationReview(
		@PathVariable Long concertId,
		@RequestBody ExpectationReviewDTO expectationDTO) {
		expectationDTO.setConcertId(concertId);
		ExpectationReviewDTO created = expectationReviewService.createExpectationReview(expectationDTO);
		return ResponseEntity.ok(SuccessResponse.of("기대평이 작성되었습니다.", created));
	}

	/**
	 * 기대평 수정
	 */
	@PutMapping("/{expectationId}")
	public ResponseEntity<SuccessResponse<ExpectationReviewDTO>> updateExpectationReview(
		@PathVariable Long concertId,
		@PathVariable Long expectationId,
		@RequestBody ExpectationReviewDTO expectationDTO) {
		return expectationReviewService.updateExpectationReview(expectationId, expectationDTO)
			.map(review -> ResponseEntity.ok(SuccessResponse.of("기대평이 수정되었습니다.", review)))
			.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * 기대평 삭제
	 */
	@DeleteMapping("/{expectationId}")
	public ResponseEntity<SuccessResponse<Void>> deleteExpectationReview(
		@PathVariable Long concertId,
		@PathVariable Long expectationId) {
		boolean deleted = expectationReviewService.deleteExpectationReview(expectationId);
		return deleted ? ResponseEntity.ok(SuccessResponse.of("기대평이 삭제되었습니다.", null)) : ResponseEntity.notFound().build();
	}
}

