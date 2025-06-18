package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.seat.service.SeatCacheInitService;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 좌석 관리자 컨트롤러 (관리자 전용)
 * - 좌석 캐시 초기화/삭제
 * - 만료된 선점 좌석 정리
 * - 캐시 상태 모니터링
 *
 * 🔒 보안: 모든 API는 ADMIN 권한 필요 (테스트 진행이므로 주석 처리)
 */
@Tag(name = "좌석 관리자", description = "관리자 전용 좌석 캐시 관리 API")
@Slf4j
@RestController
@RequestMapping("/api/admin/seats")
@RequiredArgsConstructor
public class SeatAdminController {

    private final SeatCacheInitService seatCacheInitService;
    private final SeatStatusService seatStatusService;

    /**
     * 좌석 캐시 초기화 (🔴 고위험 - 실제 서비스에서는 권한 활성화 필요)
     */
    @Operation(summary = "좌석 캐시 초기화", description = "특정 콘서트의 좌석 상태 캐시를 초기화합니다")
    // @PreAuthorize("hasRole('ADMIN')") // ← 📌 실제 서비스에서는 주석 해제
    @PostMapping("/concerts/{concertId}/cache/init")
    public ResponseEntity<SuccessResponse<String>> initSeatCache(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "총 좌석 수", example = "100")
            @RequestParam(defaultValue = "100") int totalSeats) {

        try {
            seatCacheInitService.initializeSeatCache(concertId, totalSeats);

            log.info("좌석 캐시 초기화 완료: concertId={}, totalSeats={}", concertId, totalSeats);
            return ResponseEntity.ok(SuccessResponse.of("좌석 캐시 초기화 성공", "SUCCESS"));

        } catch (Exception e) {
            log.error("좌석 캐시 초기화 중 오류: concertId={}, totalSeats={}", concertId, totalSeats, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 캐시 초기화 중 오류가 발생했습니다.", null));
        }
    }

    /**
     * 좌석 캐시 상태 조회 (🟡 위험 - 실제 서비스에서는 권한 활성화 필요)
     */
    @Operation(summary = "좌석 캐시 상태 조회", description = "특정 콘서트의 캐시 상태를 조회합니다")
    // @PreAuthorize("hasRole('ADMIN')") // ← 📌 실제 서비스에서는 주석 해제
    @GetMapping("/concerts/{concertId}/cache/status")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getCacheStatus(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            Map<String, Object> cacheStatus = seatCacheInitService.getCacheStatus(concertId);

            return ResponseEntity.ok(SuccessResponse.of("캐시 상태 조회 성공", cacheStatus));

        } catch (Exception e) {
            log.error("캐시 상태 조회 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("캐시 상태 조회 중 오류가 발생했습니다.", null));
        }
    }

    /**
     * 좌석 캐시 삭제 (🔴 고위험 - 실제 서비스에서는 권한 활성화 필요)
     */
    @Operation(summary = "좌석 캐시 삭제 (관리자 전용)", description = "특정 콘서트의 좌석 캐시를 삭제합니다")
    // @PreAuthorize("hasRole('ADMIN')") // ← 📌 실제 서비스에서는 주석 해제
    @DeleteMapping("/concerts/{concertId}/cache")
    public ResponseEntity<SuccessResponse<String>> clearSeatCache(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            String resultMessage = seatCacheInitService.clearSeatCache(concertId);

            log.info("좌석 캐시 삭제 요청 처리 완료: concertId={}, result={}",
                    concertId, resultMessage);

            return ResponseEntity.ok(SuccessResponse.of(resultMessage, "SUCCESS"));

        } catch (Exception e) {
            log.error("좌석 캐시 삭제 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 캐시 삭제 중 오류가 발생했습니다.", null));
        }
    }

    /**
     * 만료된 선점 좌석 정리 (🔴 고위험 - 실제 서비스에서는 권한 활성화 필요)
     */
    @Operation(summary = "만료된 선점 좌석 정리 (관리자 전용)", description = "만료된 선점 좌석들을 일괄 정리합니다")
    // @PreAuthorize("hasRole('ADMIN')") // ← 📌 실제 서비스에서는 주석 해제
    @PostMapping("/concerts/{concertId}/cleanup")
    public ResponseEntity<SuccessResponse<String>> cleanupExpiredReservations(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            seatStatusService.cleanupExpiredReservations(concertId);

            log.info("만료된 선점 좌석 정리 완료: concertId={}", concertId);
            return ResponseEntity.ok(SuccessResponse.of("만료된 선점 좌석 정리 성공", "SUCCESS"));

        } catch (Exception e) {
            log.error("만료된 선점 좌석 정리 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("만료된 선점 좌석 정리 중 오류가 발생했습니다.", null));
        }
    }
}