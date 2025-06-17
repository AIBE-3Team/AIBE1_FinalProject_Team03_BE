package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon.concert.dto.ConcertDTO;
import com.team03.ticketmon.concert.dto.ConcertSearchDTO;
import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.service.ConcertService;
import com.team03.ticketmon.concert.service.CacheService;
import com.team03.ticketmon.concert.service.ReviewService;
import com.team03.ticketmon._global.exception.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

/**
 * Concert Controller
 * 콘서트 관련 HTTP 요청 처리
 */

@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
public class ConcertController {

	private final ConcertService concertService;
	private final CacheService cacheService;
	private final ReviewService reviewService;

	/**
	 * 콘서트 목록 조회
	 */
	@GetMapping
	public ResponseEntity<SuccessResponse<List<ConcertDTO>>> getConcerts(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size) {
		List<ConcertDTO> concerts = concertService.getAllConcerts();
		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	/**
	 * 콘서트 검색 (타입 안전한 캐시 사용)
	 */
	@GetMapping(params = "search")
	public ResponseEntity<SuccessResponse<List<ConcertDTO>>> searchConcerts(@RequestParam String search) {
		Optional<List<ConcertDTO>> cachedResult = cacheService.getCachedSearchResults(search);
		if (cachedResult.isPresent()) {
			return ResponseEntity.ok(SuccessResponse.of(cachedResult.get()));
		}

		// 캐시 미스 시 실제 검색
		ConcertSearchDTO searchDTO = new ConcertSearchDTO();
		searchDTO.setKeyword(search);
		List<ConcertDTO> concerts = concertService.searchConcerts(searchDTO);

		// 검색 결과 캐싱
		cacheService.cacheSearchResults(search, concerts);

		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	/**
	 * 콘서트 필터링 (날짜, 가격)
	 */
	@GetMapping(params = {"date", "price_min", "price_max"})
	public ResponseEntity<SuccessResponse<List<ConcertDTO>>> filterConcerts(
		@RequestParam(required = false) String date,
		@RequestParam(name = "price_min", required = false) String priceMin,
		@RequestParam(name = "price_max", required = false) String priceMax) {

		ConcertSearchDTO searchDTO = new ConcertSearchDTO();
		List<ConcertDTO> concerts = concertService.searchConcerts(searchDTO);
		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	/**
	 * 콘서트 상세 조회 (타입 안전한 캐시 사용)
	 */
	@GetMapping("/{id}")
	public ResponseEntity<SuccessResponse<ConcertDTO>> getConcertDetail(@PathVariable Long id) {
		Optional<ConcertDTO> cachedResult = cacheService.getCachedConcertDetail(id, ConcertDTO.class);
		if (cachedResult.isPresent()) {
			return ResponseEntity.ok(SuccessResponse.of(cachedResult.get()));
		}

		// 캐시 미스 시 실제 조회
		return concertService.getConcertById(id)
			.map(concert -> {
				cacheService.cacheConcertDetail(id, concert);
				return ResponseEntity.ok(SuccessResponse.of(concert));
			})
			.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * AI 요약 정보 조회
	 */
	@GetMapping("/{id}/ai-summary")
	public ResponseEntity<SuccessResponse<String>> getAiSummary(@PathVariable Long id) {
		String summary = concertService.getAiSummary(id);
		return ResponseEntity.ok(SuccessResponse.of(summary));
	}

	/**
	 * 콘서트 후기 조회
	 */
	@GetMapping("/{id}/reviews")
	public ResponseEntity<SuccessResponse<List<ReviewDTO>>> getConcertReviews(@PathVariable Long id) {
		List<ReviewDTO> reviews = reviewService.getConcertReviews(id);
		return ResponseEntity.ok(SuccessResponse.of(reviews));
	}

	/**
	 * 예약 가능한 좌석 조회
	 */
	@GetMapping("/{id}/seats")
	public ResponseEntity<SuccessResponse<List<ConcertSeat>>> getAvailableSeats(@PathVariable Long id) {
		List<ConcertSeat> seats = concertService.getAvailableSeats(id);
		return ResponseEntity.ok(SuccessResponse.of(seats));
	}
}