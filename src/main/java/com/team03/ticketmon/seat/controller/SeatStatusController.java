package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.dto.SeatReserveRequest;
import com.team03.ticketmon.seat.dto.SeatStatusResponse;
import com.team03.ticketmon.seat.service.SeatCacheInitService;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 좌석 상태 관리 컨트롤러
 * - 좌석 상태 조회/업데이트
 * - 캐시 초기화 및 관리
 */
@Tag(name = "좌석 상태 관리", description = "Redis 기반 좌석 상태 관리 API")
@Slf4j
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatStatusController {

    private final SeatStatusService seatStatusService;
    private final SeatCacheInitService seatCacheInitService;

    @Operation(summary = "콘서트 전체 좌석 상태 조회", description = "특정 콘서트의 모든 좌석 상태를 조회합니다")
    @GetMapping("/concerts/{concertId}/status")
    public ResponseEntity<SuccessResponse<List<SeatStatusResponse>>> getAllSeatStatus(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        Map<Long, SeatStatus> seatStatusMap = seatStatusService.getAllSeatStatus(concertId);

        List<SeatStatusResponse> responses = seatStatusMap.values().stream()
                .map(SeatStatusResponse::from)
                .collect(Collectors.toList());

        log.info("전체 좌석 상태 조회: concertId={}, seatCount={}", concertId, responses.size());
        return ResponseEntity.ok(SuccessResponse.of("좌석 상태 조회 성공", responses));
    }

    @Operation(summary = "특정 좌석 상태 조회", description = "특정 좌석의 상태를 조회합니다")
    @GetMapping("/concerts/{concertId}/seats/{seatId}/status")
    public ResponseEntity<SuccessResponse<SeatStatusResponse>> getSeatStatus(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId) {

        Optional<SeatStatus> seatStatus = seatStatusService.getSeatStatus(concertId, seatId);

        if (seatStatus.isPresent()) {
            SeatStatusResponse response = SeatStatusResponse.from(seatStatus.get());
            return ResponseEntity.ok(SuccessResponse.of("좌석 상태 조회 성공", response));
        } else {
            return ResponseEntity.ok(SuccessResponse.of("좌석 정보 없음", null));
        }
    }

    @Operation(summary = "좌석 임시 선점", description = "좌석을 5분간 임시 선점합니다")
    @PostMapping("/concerts/{concertId}/seats/{seatId}/reserve")
    public ResponseEntity<SuccessResponse<SeatStatusResponse>> reserveSeat(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId,
            @RequestBody SeatReserveRequest request) {

        // 현재 좌석 상태 확인
        Optional<SeatStatus> currentStatus = seatStatusService.getSeatStatus(concertId, seatId);

        if (currentStatus.isPresent() && !currentStatus.get().isAvailable()) {
            if (currentStatus.get().isExpired()) {
                // 만료된 선점이면 해제 후 다시 선점
                seatStatusService.releaseSeat(concertId, seatId);
            } else {
                return ResponseEntity.badRequest()
                        .body(SuccessResponse.of("이미 선점되었거나 예매된 좌석입니다", null));
            }
        }

        // 좌석 정보 생성 (실제로는 DB에서 조회)
        String seatInfo = generateSeatInfo(seatId.intValue());

        SeatStatus reservedSeat = seatStatusService.reserveSeat(
                concertId, seatId, request.userId(), seatInfo);

        SeatStatusResponse response = SeatStatusResponse.from(reservedSeat);

        log.info("좌석 선점 완료: concertId={}, seatId={}, userId={}",
                concertId, seatId, request.userId());

        return ResponseEntity.ok(SuccessResponse.of("좌석 선점 성공", response));
    }

    @Operation(summary = "좌석 선점 해제", description = "임시 선점된 좌석을 해제합니다")
    @PostMapping("/concerts/{concertId}/seats/{seatId}/release")
    public ResponseEntity<SuccessResponse<String>> releaseSeat(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId) {

        seatStatusService.releaseSeat(concertId, seatId);

        log.info("좌석 선점 해제: concertId={}, seatId={}", concertId, seatId);
        return ResponseEntity.ok(SuccessResponse.of("좌석 선점 해제 성공", "SUCCESS"));
    }

    @Operation(summary = "사용자 선점 좌석 조회", description = "특정 사용자가 선점한 좌석들을 조회합니다")
    @GetMapping("/concerts/{concertId}/users/{userId}/reserved")
    public ResponseEntity<SuccessResponse<List<SeatStatusResponse>>> getUserReservedSeats(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId) {

        List<SeatStatus> reservedSeats = seatStatusService.getUserReservedSeats(concertId, userId);

        List<SeatStatusResponse> responses = reservedSeats.stream()
                .map(SeatStatusResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(SuccessResponse.of("사용자 선점 좌석 조회 성공", responses));
    }

    // === 캐시 관리 API ===

    @Operation(summary = "좌석 캐시 초기화", description = "특정 콘서트의 좌석 상태 캐시를 초기화합니다")
    @PostMapping("/concerts/{concertId}/cache/init")
    public ResponseEntity<SuccessResponse<String>> initSeatCache(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "총 좌석 수", example = "100")
            @RequestParam(defaultValue = "100") int totalSeats) {

        seatCacheInitService.initializeSeatCache(concertId, totalSeats);

        log.info("좌석 캐시 초기화 완료: concertId={}, totalSeats={}", concertId, totalSeats);
        return ResponseEntity.ok(SuccessResponse.of("좌석 캐시 초기화 성공", "SUCCESS"));
    }

    @Operation(summary = "좌석 캐시 상태 조회", description = "특정 콘서트의 캐시 상태를 조회합니다")
    @GetMapping("/concerts/{concertId}/cache/status")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getCacheStatus(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        Map<String, Object> cacheStatus = seatCacheInitService.getCacheStatus(concertId);

        return ResponseEntity.ok(SuccessResponse.of("캐시 상태 조회 성공", cacheStatus));
    }

    @Operation(summary = "좌석 캐시 삭제", description = "특정 콘서트의 좌석 캐시를 삭제합니다")
    @DeleteMapping("/concerts/{concertId}/cache")
    public ResponseEntity<SuccessResponse<String>> clearSeatCache(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        seatCacheInitService.clearSeatCache(concertId);

        log.info("좌석 캐시 삭제 완료: concertId={}", concertId);
        return ResponseEntity.ok(SuccessResponse.of("좌석 캐시 삭제 성공", "SUCCESS"));
    }

    @Operation(summary = "만료된 선점 좌석 정리", description = "만료된 선점 좌석들을 일괄 정리합니다")
    @PostMapping("/concerts/{concertId}/cleanup")
    public ResponseEntity<SuccessResponse<String>> cleanupExpiredReservations(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        seatStatusService.cleanupExpiredReservations(concertId);

        log.info("만료된 선점 좌석 정리 완료: concertId={}", concertId);
        return ResponseEntity.ok(SuccessResponse.of("만료된 선점 좌석 정리 성공", "SUCCESS"));
    }

    /**
     * 좌석 정보 문자열 생성 (더미 데이터용)
     * 실제 운영에서는 DB에서 조회해야 함
     */
    private String generateSeatInfo(int seatNumber) {
        String section;
        int seatInSection;

        if (seatNumber <= 50) {
            section = "A";
            seatInSection = seatNumber;
        } else if (seatNumber <= 100) {
            section = "B";
            seatInSection = seatNumber - 50;
        } else {
            section = "C";
            seatInSection = seatNumber - 100;
        }

        return section + "-" + seatInSection;
    }
}