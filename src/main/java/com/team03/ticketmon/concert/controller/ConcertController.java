package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon.concert.dto.ConcertDTO;
import com.team03.ticketmon.concert.dto.ConcertFilterDTO;
import com.team03.ticketmon.concert.dto.ConcertSearchDTO;
import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.service.ConcertService;
import com.team03.ticketmon.concert.service.CacheService;
import com.team03.ticketmon.concert.service.ReviewService;
import com.team03.ticketmon._global.exception.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Concert Controller
 * 콘서트 관련 HTTP 요청 처리
 */
@Tag(name = "콘서트 API", description = "콘서트 조회, 검색, 필터링 관련 API")
@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
@Validated
public class ConcertController {

	private final ConcertService concertService;
	private final CacheService cacheService;
	private final ReviewService reviewService;

	@Operation(
		summary = "콘서트 목록 조회",
		description = """
		활성 상태 콘서트 목록을 페이징으로 조회합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "콘서트 목록 조회 성공",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = SuccessResponse.class),
				examples = @ExampleObject(
					name = "성공 응답 예시",
					value = """
					{
						"success": true,
						"message": "성공",
						"data": {
							"content": [
								{
									"concertId": 1,
									"title": "아이유 콘서트 2025",
									"artist": "아이유",
									"status": "ON_SALE",
									"venueName": "올림픽공원 체조경기장",
									"concertDate": "2025-08-15",
									"startTime": "19:00:00",
									"totalSeats": 8000
								}
							],
							"totalElements": 50,
							"totalPages": 3,
							"size": 20,
							"number": 0,
							"first": true,
							"last": false
						}
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 페이징 파라미터",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "페이지 크기는 1~100 사이여야 합니다",
						"data": null
					}
					"""
				)
			)
		)
	})
	@GetMapping
	public ResponseEntity<SuccessResponse<Page<ConcertDTO>>> getConcerts(
		@Parameter(
			description = "**페이지 번호** (0부터 시작)",
			example = "0",
			schema = @Schema(minimum = "0", defaultValue = "0")
		)
		@RequestParam(defaultValue = "0") @Min(0) int page,

		@Parameter(
			description = "**페이지 크기** (1~100개)",
			example = "20",
			schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")
		)
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

		Page<ConcertDTO> concerts = concertService.getAllConcerts(page, size);
		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	@Operation(
		summary = "콘서트 키워드 검색",
		description = """
		키워드를 통해 콘서트를 검색합니다. (캐시 적용)
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "콘서트 검색 성공",
			content = @Content(
				mediaType = "application/json",
				examples = {
					@ExampleObject(
						name = "검색 결과 있음",
						value = """
						{
							"success": true,
							"message": "성공",
							"data": [
								{
									"concertId": 1,
									"title": "아이유 콘서트 2025",
									"artist": "아이유",
									"status": "ON_SALE",
									"venueName": "올림픽공원 체조경기장",
									"concertDate": "2025-08-15"
								}
							]
						}
						"""
					),
					@ExampleObject(
						name = "검색 결과 없음",
						value = """
						{
							"success": true,
							"message": "성공",
							"data": []
						}
						"""
					)
				}
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 검색 키워드",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "검색 키워드는 1자 이상 100자 이하여야 합니다",
						"data": null
					}
					"""
				)
			)
		)
	})
	@GetMapping("/search")
	public ResponseEntity<SuccessResponse<List<ConcertDTO>>> searchConcerts(
		@Parameter(
			description = """
			**검색 키워드**
			- 콘서트 제목, 아티스트명, 공연장명 검색
			- 1~100자, 공백만으로는 검색 불가
			""",
			example = "아이유",
			schema = @Schema(minLength = 1, maxLength = 100)
		)
		@RequestParam String query) {

		// 캐시 조회 시도
		Optional<List<ConcertDTO>> cachedResult = cacheService.getCachedSearchResults(query, ConcertDTO.class);
		if (cachedResult.isPresent()) {
			return ResponseEntity.ok(SuccessResponse.of(cachedResult.get()));
		}

		// 캐시 미스 시 실제 검색
		ConcertSearchDTO searchDTO = new ConcertSearchDTO();
		searchDTO.setKeyword(query);
		List<ConcertDTO> concerts = concertService.searchConcerts(searchDTO);

		// 검색 결과 캐싱
		cacheService.cacheSearchResults(query, concerts);

		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	@Operation(
		summary = "콘서트 고급 필터링",
		description = """
		날짜와 가격 범위로 콘서트를 정밀 필터링합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "필터링 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "필터링 결과",
					value = """
					{
						"success": true,
						"message": "성공",
						"data": [
							{
								"concertId": 2,
								"title": "BTS 월드투어 서울",
								"artist": "BTS",
								"status": "ON_SALE",
								"concertDate": "2025-09-20",
								"venueName": "잠실올림픽주경기장"
							}
						]
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 필터 조건",
			content = @Content(
				examples = {
					@ExampleObject(
						name = "날짜 순서 오류",
						value = """
						{
							"success": false,
							"message": "종료 날짜는 시작 날짜와 같거나 늦어야 합니다",
							"data": null
						}
						"""
					),
					@ExampleObject(
						name = "가격 범위 초과",
						value = """
						{
							"success": false,
							"message": "가격 범위가 너무 큽니다",
							"data": null
						}
						"""
					)
				}
			)
		)
	})

	@GetMapping("/filter")
	public ResponseEntity<SuccessResponse<List<ConcertDTO>>> filterConcerts(@Valid @ModelAttribute ConcertFilterDTO filterDTO) {

		List<ConcertDTO> concerts = concertService.applyFilters(filterDTO);
		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	@Operation(
		summary = "콘서트 상세 조회",
		description = """
		콘서트 ID로 상세 정보를 조회합니다. (캐시 적용)
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "콘서트 상세 조회 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "상세 정보 예시",
					value = """
					{
						"success": true,
						"message": "성공",
						"data": {
							"concertId": 1,
							"title": "아이유 콘서트 2025 'HEREH WORLD TOUR'",
							"artist": "아이유",
							"description": "아이유의 2025년 월드투어 서울 공연",
							"sellerId": 100,
							"venueName": "올림픽공원 체조경기장",
							"venueAddress": "서울특별시 송파구 올림픽로 424",
							"concertDate": "2025-08-15",
							"startTime": "19:00:00",
							"endTime": "21:30:00",
							"totalSeats": 8000,
							"bookingStartDate": "2025-07-01T10:00:00",
							"bookingEndDate": "2025-08-14T23:59:59",
							"minAge": 0,
							"maxTicketsPerUser": 4,
							"status": "ON_SALE",
							"posterImageUrl": "https://example.com/posters/iu-2025.jpg",
							"aiSummary": "아이유의 2025년 새 앨범 발매 기념 월드투어 서울 공연으로, 신곡과 대표곡을 함께 들을 수 있는 특별한 무대입니다."
						}
					}
					"""
				)
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 콘서트 ID (1 이상의 양수 필요)"),
		@ApiResponse(responseCode = "404", description = "콘서트를 찾을 수 없음")
	})
	@GetMapping("/{id}")
	public ResponseEntity<SuccessResponse<ConcertDTO>> getConcertDetail(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long id) {

		// 캐시 조회 시도
		Optional<ConcertDTO> cachedResult = cacheService.getCachedConcertDetail(id, ConcertDTO.class);
		if (cachedResult.isPresent()) {
			return ResponseEntity.ok(SuccessResponse.of(cachedResult.get()));
		}

		// 캐시 미스 시 실제 조회
		Optional<ConcertDTO> concertOpt = concertService.getConcertById(id);
		if (concertOpt.isPresent()) {
			cacheService.cacheConcertDetail(id, concertOpt.get());
			return ResponseEntity.ok(SuccessResponse.of(concertOpt.get()));
		}
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(SuccessResponse.of(null));
	}

	@Operation(
		summary = "AI 요약 정보 조회",
		description = """
		콘서트의 AI 생성 요약 정보를 조회합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "AI 요약 조회 성공",
			content = @Content(
				mediaType = "application/json",
				examples = {
					@ExampleObject(
						name = "AI 요약 있는 경우",
						value = """
						{
							"success": true,
							"message": "성공",
							"data": "아이유의 2025년 새 앨범 'HEREH' 발매 기념 월드투어 서울 공연으로, '좋은 날', 'Through the Night' 등 대표곡과 신곡을 함께 들을 수 있는 특별한 무대입니다. 친밀한 소통과 감성적인 라이브로 유명한 아이유의 진정성 있는 공연을 만나보세요."
						}
						"""
					),
					@ExampleObject(
						name = "AI 요약 없는 경우",
						value = """
						{
							"success": true,
							"message": "성공",
							"data": "AI 요약 정보가 아직 생성되지 않았습니다."
						}
						"""
					)
				}
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 콘서트 ID"),
		@ApiResponse(responseCode = "404", description = "콘서트를 찾을 수 없음")
	})
	@GetMapping("/{id}/ai-summary")
	public ResponseEntity<SuccessResponse<String>> getAiSummary(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long id) {

		String summary = concertService.getAiSummary(id);
		return ResponseEntity.ok(SuccessResponse.of(summary));
	}

	@Operation(
		summary = "콘서트 후기 목록 조회",
		description = """
		특정 콘서트의 후기 목록을 페이징으로 조회합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "후기 목록 조회 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "후기 목록 예시",
					value = """
					{
						"success": true,
						"message": "성공",
						"data": {
							"content": [
								{
									"id": 1,
									"userNickname": "음악매니아123",
									"title": "정말 감동적인 공연이었어요!",
									"description": "아이유의 라이브 실력이 정말 대단했습니다. 특히 'Through the Night' 무대에서는 눈물이 날 뻔했어요.",
									"rating": 5,
									"createdAt": "2025-06-20T10:30:00",
									"updatedAt": "2025-06-20T10:30:00"
								}
							],
							"totalElements": 127,
							"totalPages": 13,
							"size": 10,
							"number": 0,
							"first": true,
							"last": false
						}
					}
					"""
				)
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 페이징 파라미터 또는 콘서트 ID"),
		@ApiResponse(responseCode = "404", description = "콘서트를 찾을 수 없음")
	})
	@GetMapping("/{id}/reviews")
	public ResponseEntity<SuccessResponse<Page<ReviewDTO>>> getConcertReviews(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable("id") @Min(1) Long concertId,

		@Parameter(
			description = "**페이지 번호** (0부터 시작)",
			example = "0",
			schema = @Schema(minimum = "0", defaultValue = "0")
		)
		@RequestParam(defaultValue = "0") @Min(0) int page,

		@Parameter(
			description = "**페이지 크기** (1~100개)",
			example = "10",
			schema = @Schema(minimum = "1", maximum = "100", defaultValue = "10")
		)
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {

		// 고정: 최신순 정렬
		Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

		Page<ReviewDTO> reviews = reviewService.getConcertReviews(concertId, pageable);

		return ResponseEntity.ok(SuccessResponse.of(reviews));
	}
}