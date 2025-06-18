package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon.concert.dto.ExpectationReviewDTO;
import com.team03.ticketmon.concert.service.ExpectationReviewService;
import com.team03.ticketmon._global.exception.SuccessResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
	public ResponseEntity<SuccessResponse<Page<ExpectationReviewDTO>>> getConcertExpectationReviews(@PathVariable Long concertId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "10") int size) {

		// 고정: 최신순 정렬
		Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

		Page<ExpectationReviewDTO> expectations =
			expectationReviewService.getConcertExpectationReviews(concertId, pageable);

		return ResponseEntity.ok(SuccessResponse.of(expectations));
	}

	/**
	 * 기대평 작성
	 */
	@PostMapping
	public ResponseEntity<SuccessResponse<ExpectationReviewDTO>> createExpectationReview(
		@PathVariable Long concertId,
		@Valid @RequestBody ExpectationReviewDTO expectationDTO) {
		expectationDTO.setConcertId(concertId);
		ExpectationReviewDTO created = expectationReviewService.createExpectationReview(expectationDTO);
		return ResponseEntity.status(HttpStatus.CREATED)
                      .body(SuccessResponse.of("기대평이 작성되었습니다.", created));
	}

	/**
	 * 기대평 수정
	 */
	@PutMapping("/{expectationId}")
	public ResponseEntity<SuccessResponse<ExpectationReviewDTO>> updateExpectationReview(
		@PathVariable Long concertId,
		@PathVariable Long expectationId,
		@Valid @RequestBody ExpectationReviewDTO expectationDTO) {
		return expectationReviewService.updateExpectationReview(concertId, expectationId, expectationDTO)
			.map(review -> ResponseEntity.ok(SuccessResponse.of("기대평이 수정되었습니다.", review)))
			.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(SuccessResponse.of("기대평을 찾을 수 없습니다.", null)));
	}

	/**
	 * 기대평 삭제
	 */
	@DeleteMapping("/{expectationId}")
	public ResponseEntity<SuccessResponse<Void>> deleteExpectationReview(
		@PathVariable Long concertId,
		@PathVariable Long expectationId) {
		boolean deleted = expectationReviewService.deleteExpectationReview(concertId, expectationId);
		return deleted ?
			ResponseEntity.ok(SuccessResponse.of("기대평이 삭제되었습니다.", null)) :
			ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(SuccessResponse.of("기대평을 찾을 수 없습니다.", null));
	}
}

