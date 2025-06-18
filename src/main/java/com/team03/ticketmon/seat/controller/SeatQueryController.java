package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.dto.SeatStatusResponse;
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
 * 좌석 상태 조회 컨트롤러 (읽기 전용)
 * - 콘서트 전체 좌석 상태 조회
 * - 개별 좌석 상태 조회
 * - 사용자 선점 좌석 조회
 */
@Tag(name = "좌석 상태 조회", description = "좌석 상태 읽기 전용 API")
@Slf4j
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatQueryController {

    private final SeatStatusService seatStatusService;

    @Operation(summary = "콘서트 전체 좌석 상태 조회", description = "특정 콘서트의 모든 좌석 상태를 조회합니다")
    @GetMapping("/concerts/{concertId}/status")
    public ResponseEntity<SuccessResponse<List<SeatStatusResponse>>> getAllSeatStatus(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            Map<Long, SeatStatus> seatStatusMap = seatStatusService.getAllSeatStatus(concertId);

            List<SeatStatusResponse> responses = seatStatusMap.values().stream()
                    .map(SeatStatusResponse::from)
                    .collect(Collectors.toList());

            log.info("전체 좌석 상태 조회 성공: concertId={}, seatCount={}", concertId, responses.size());
            return ResponseEntity.ok(SuccessResponse.of("좌석 상태 조회 성공", responses));

        } catch (Exception e) {
            log.error("전체 좌석 상태 조회 중 오류 발생: concertId={}", concertId, e);

            // Redis 연결 오류 등 인프라 문제인지 확인
            if (e.getCause() != null && e.getCause().getMessage().contains("Redis")) {
                return ResponseEntity.status(503) // Service Unavailable
                        .body(SuccessResponse.of("일시적인 서비스 장애입니다. 잠시 후 다시 시도해주세요.", null));
            }

            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 상태 조회 중 오류가 발생했습니다.", null));
        }
    }

    @Operation(summary = "특정 좌석 상태 조회", description = "특정 좌석의 상태를 조회합니다")
    @GetMapping("/concerts/{concertId}/seats/{seatId}/status")
    public ResponseEntity<SuccessResponse<SeatStatusResponse>> getSeatStatus(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId) {

        try {
            Optional<SeatStatus> seatStatus = seatStatusService.getSeatStatus(concertId, seatId);

            if (seatStatus.isPresent()) {
                SeatStatusResponse response = SeatStatusResponse.from(seatStatus.get());
                return ResponseEntity.ok(SuccessResponse.of("좌석 상태 조회 성공", response));
            } else {
                return ResponseEntity.ok(SuccessResponse.of("좌석 정보 없음", null));
            }

        } catch (Exception e) {
            log.error("좌석 상태 조회 중 오류 발생: concertId={}, seatId={}", concertId, seatId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 상태 조회 중 오류가 발생했습니다.", null));
        }
    }

    @Operation(summary = "사용자 선점 좌석 조회", description = "특정 사용자가 선점한 좌석들을 조회합니다")
    @GetMapping("/concerts/{concertId}/users/{userId}/reserved")
    public ResponseEntity<SuccessResponse<List<SeatStatusResponse>>> getUserReservedSeats(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId) {

        try {
            List<SeatStatus> reservedSeats = seatStatusService.getUserReservedSeats(concertId, userId);

            List<SeatStatusResponse> responses = reservedSeats.stream()
                    .map(SeatStatusResponse::from)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(SuccessResponse.of("사용자 선점 좌석 조회 성공", responses));

        } catch (Exception e) {
            log.error("사용자 선점 좌석 조회 중 오류: concertId={}, userId={}", concertId, userId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("사용자 선점 좌석 조회 중 오류가 발생했습니다.", null));
        }
    }
}