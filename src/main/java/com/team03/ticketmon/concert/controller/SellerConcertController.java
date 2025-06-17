package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon.concert.dto.*;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.service.SellerConcertService;
import com.team03.ticketmon._global.exception.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Seller Concert Controller
 * 판매자용 콘서트 관련 HTTP 요청 처리
 */

@RestController
@RequestMapping("/api/seller/concerts")
@RequiredArgsConstructor
public class SellerConcertController {

	private final SellerConcertService sellerConcertService;

	/**
	 * 판매자 콘서트 목록 조회 (페이징)
	 */
	@GetMapping
	public ResponseEntity<SuccessResponse<Page<SellerConcertDTO>>> getSellerConcerts(
		@RequestParam Long sellerId,
		@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

		Page<SellerConcertDTO> concerts = sellerConcertService
			.getSellerConcerts(sellerId, pageable);

		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	/**
	 * 판매자별 상태별 콘서트 조회
	 */
	@GetMapping("/status")
	public ResponseEntity<SuccessResponse<List<SellerConcertDTO>>> getSellerConcertsByStatus(
		@RequestParam Long sellerId,
		@RequestParam ConcertStatus status) {

		List<SellerConcertDTO> concerts = sellerConcertService
			.getSellerConcertsByStatus(sellerId, status);

		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	/**
	 * 콘서트 생성
	 */
	@PostMapping
	public ResponseEntity<SuccessResponse<SellerConcertDTO>> createConcert(
		@RequestParam Long sellerId,
		@RequestBody SellerConcertCreateDTO createDTO) {

		SellerConcertDTO createdConcert = sellerConcertService
			.createConcert(sellerId, createDTO);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(SuccessResponse.of("콘서트가 생성되었습니다.", createdConcert));
	}

	/**
	 * 콘서트 수정
	 */
	@PutMapping("/{concertId}")
	public ResponseEntity<SuccessResponse<SellerConcertDTO>> updateConcert(
		@RequestParam Long sellerId,
		@PathVariable Long concertId,
		@RequestBody SellerConcertUpdateDTO updateDTO) {

		SellerConcertDTO updatedConcert = sellerConcertService
			.updateConcert(sellerId, concertId, updateDTO);

		return ResponseEntity.ok(SuccessResponse.of("콘서트가 수정되었습니다.", updatedConcert));
	}

	/**
	 * 포스터 이미지 업데이트
	 */
	@PatchMapping("/{concertId}/poster")
	public ResponseEntity<SuccessResponse<Void>> updatePosterImage(
		@RequestParam Long sellerId,
		@PathVariable Long concertId,
		@RequestBody SellerConcertImageUpdateDTO imageDTO) {

		sellerConcertService.updatePosterImage(sellerId, concertId, imageDTO);
		return ResponseEntity.ok(SuccessResponse.of("포스터 이미지가 업데이트되었습니다.", null));
	}

	/**
	 * 콘서트 삭제 (취소 처리)
	 */
	@DeleteMapping("/{concertId}")
	public ResponseEntity<SuccessResponse<Void>> deleteConcert(
		@RequestParam Long sellerId,
		@PathVariable Long concertId) {

		sellerConcertService.deleteConcert(sellerId, concertId);
		return ResponseEntity.ok(SuccessResponse.of("콘서트가 취소되었습니다.", null));
	}

	/**
	 * 판매자 콘서트 개수 조회
	 */
	@GetMapping("/count")
	public ResponseEntity<SuccessResponse<Long>> getSellerConcertCount(
		@RequestParam Long sellerId) {

		long count = sellerConcertService.getSellerConcertCount(sellerId);
		return ResponseEntity.ok(SuccessResponse.of(count));
	}
}