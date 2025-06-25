package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.seat.service.SeatPollingSessionManager;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 좌석 상태 실시간 폴링 컨트롤러
 * - Long Polling 방식으로 좌석 상태 변경사항을 실시간으로 클라이언트에 전달
 * - DeferredResult를 활용한 비동기 응답 처리
 */
@Tag(name = "좌석 실시간 폴링", description = "좌석 상태 실시간 업데이트 API (Long Polling)")
@Slf4j
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatPollingController {

    private final SeatPollingSessionManager sessionManager;
    private final SeatStatusService seatStatusService;

    // Long Polling 설정
    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30초
    private static final long MAX_TIMEOUT_MS = 60000; // 최대 60초
    private static final int MAX_SESSIONS_PER_CONCERT = 1000; // 콘서트당 최대 세션

    /**
     * 좌석 상태 실시간 폴링 API
     * - 좌석 상태 변경 시 즉시 응답, 변경사항 없으면 타임아웃까지 대기
     *
     * @param concertId 콘서트 ID
     * @param lastUpdateTime 클라이언트가 마지막으로 받은 업데이트 시간 (선택적)
     * @param timeout 폴링 타임아웃 (ms, 기본 30초)
     * @param userId 사용자 ID (선택적)
     * @return DeferredResult로 비동기 응답
     */
    @Operation(summary = "좌석 상태 실시간 폴링",
            description = "좌석 상태 변경 시 즉시 응답, 변경사항 없으면 최대 30초 대기")
    @GetMapping("/concerts/{concertId}/polling")
    public DeferredResult<ResponseEntity<?>> pollSeatUpdates(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,

            @Parameter(description = "마지막 업데이트 시간 (ISO 형식)", example = "2025-06-23T10:30:00")
            @RequestParam(required = false) String lastUpdateTime,

            @Parameter(description = "폴링 타임아웃 (밀리초)", example = "30000")
            @RequestParam(defaultValue = "30000") long timeout,

            @Parameter(description = "사용자 ID", example = "100")
            @RequestParam(required = false) Long userId) {

        // 타임아웃 범위 검증
        timeout = Math.min(Math.max(timeout, 5000), MAX_TIMEOUT_MS);

        // DeferredResult 생성
        DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>(timeout);

        try {
            // 세션 수 제한 확인
            if (sessionManager.getSessionCount(concertId) >= MAX_SESSIONS_PER_CONCERT) {
                Map<String, Object> overloadResponse = Map.of(
                        "hasUpdate", false,
                        "message", "서버 과부하로 인해 즉시 응답합니다. 잠시 후 다시 시도해주세요.",
                        "recommendedRetryAfter", 5000
                );
                deferredResult.setResult(ResponseEntity.ok(SuccessResponse.of("서버 과부하", overloadResponse)));
                return deferredResult;
            }

            // 최근 업데이트 시간 파싱 및 즉시 응답 여부 확인
            LocalDateTime lastUpdate = parseLastUpdateTime(lastUpdateTime);
            if (lastUpdate != null && hasRecentUpdates(concertId, lastUpdate)) {
                // 최근 1초 이내 변경사항이 있으면 즉시 현재 상태 응답
                Map<String, Object> immediateResponse = getCurrentSeatStatusResponse(concertId);
                deferredResult.setResult(ResponseEntity.ok(SuccessResponse.of("즉시 응답", immediateResponse)));
                return deferredResult;
            }

            // 세션 등록
            String sessionId = sessionManager.registerSession(concertId, deferredResult, userId);
            if (sessionId == null) {
                // 세션 등록 실패 (서버 과부하)
                Map<String, Object> failResponse = Map.of(
                        "hasUpdate", false,
                        "message", "세션 등록 실패. 잠시 후 다시 시도해주세요."
                );
                deferredResult.setResult(ResponseEntity.status(503)
                        .body(SuccessResponse.of("서비스 일시 불가", failResponse)));
                return deferredResult;
            }

            // 타임아웃 핸들러 설정
            deferredResult.onTimeout(() -> {
                Map<String, Object> timeoutResponse = Map.of(
                        "hasUpdate", false,
                        "message", "폴링 타임아웃",
                        "nextPollRecommended", true
                );
                deferredResult.setResult(ResponseEntity.ok(SuccessResponse.of("타임아웃", timeoutResponse)));
            });

            // 에러 핸들러 설정
            deferredResult.onError(throwable -> {
                log.error("폴링 세션 에러: concertId={}, sessionId={}, userId={}",
                        concertId, sessionId, userId, throwable);
                Map<String, Object> errorResponse = Map.of(
                        "hasUpdate", false,
                        "message", "폴링 중 오류 발생"
                );
                deferredResult.setResult(ResponseEntity.status(500)
                        .body(SuccessResponse.of("서버 오류", errorResponse)));
            });

            log.debug("폴링 세션 시작: concertId={}, sessionId={}, userId={}, timeout={}ms",
                    concertId, sessionId, userId, timeout);

        } catch (Exception e) {
            log.error("폴링 요청 처리 중 오류: concertId={}, userId={}", concertId, userId, e);
            Map<String, Object> errorResponse = Map.of(
                    "hasUpdate", false,
                    "message", "폴링 요청 처리 실패"
            );
            deferredResult.setResult(ResponseEntity.status(500)
                    .body(SuccessResponse.of("요청 처리 실패", errorResponse)));
        }

        return deferredResult;
    }

    /**
     * 폴링 시스템 상태 조회 (디버깅/모니터링용)
     */
    @Operation(summary = "폴링 시스템 상태 조회", description = "현재 활성 폴링 세션 정보 조회")
    @GetMapping("/concerts/{concertId}/polling/status")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getPollingStatus(
            @PathVariable Long concertId) {

        Map<String, Object> status = Map.of(
                "concertId", concertId,
                "activeSessionCount", sessionManager.getSessionCount(concertId),
                "totalActiveSessionCount", sessionManager.getTotalSessionCount(),
                "maxSessionsPerConcert", MAX_SESSIONS_PER_CONCERT,
                "defaultTimeout", DEFAULT_TIMEOUT_MS,
                "serverTime", LocalDateTime.now()
        );

        return ResponseEntity.ok(SuccessResponse.of("폴링 상태 조회 성공", status));
    }

    /**
     * 마지막 업데이트 시간 파싱
     */
    private LocalDateTime parseLastUpdateTime(String lastUpdateTime) {
        if (lastUpdateTime == null || lastUpdateTime.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(lastUpdateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("잘못된 시간 형식: {}", lastUpdateTime);
            return null;
        }
    }

    /**
     * 최근 업데이트 여부 확인 (간단 구현)
     * TODO: 실제로는 Redis에서 최근 변경 시간을 확인해야 함
     */
    private boolean hasRecentUpdates(Long concertId, LocalDateTime lastUpdate) {
        // 현재는 간단히 1초 이내 변경사항이 있다고 가정
        // 실제 구현에서는 Redis에 마지막 업데이트 시간을 저장하여 비교
        return false;
    }

    /**
     * 현재 좌석 상태 응답 생성
     */
    private Map<String, Object> getCurrentSeatStatusResponse(Long concertId) {
        return Map.of(
                "hasUpdate", true,
                "updateTime", LocalDateTime.now(),
                "seatUpdates", Map.of(), // 실제로는 변경된 좌석 정보
                "message", "현재 상태 조회"
        );
    }
}