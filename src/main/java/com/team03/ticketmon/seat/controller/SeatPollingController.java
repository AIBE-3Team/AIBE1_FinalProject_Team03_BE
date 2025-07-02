package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.seat.service.SeatPollingSessionManager;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * 좌석 상태 실시간 폴링 컨트롤러 (개선된 버전)
 * - Long Polling 방식으로 좌석 상태 변경사항을 실시간으로 클라이언트에 전달
 * - DeferredResult를 활용한 비동기 응답 처리
 * - 성능 최적화 및 오류 처리 강화
 */
@Tag(name = "좌석 실시간 폴링", description = "좌석 상태 실시간 업데이트 API (Long Polling)")
@Slf4j
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatPollingController {

    private final SeatPollingSessionManager sessionManager;
    private final SeatStatusService seatStatusService;

    // Long Polling 설정 (환경별 조정 가능하도록 개선)
    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30초
    private static final long MAX_TIMEOUT_MS = 60000; // 최대 60초
    private static final long MIN_TIMEOUT_MS = 5000; // 최소 5초
    private static final int MAX_SESSIONS_PER_CONCERT = 1000; // 콘서트당 최대 세션

    /**
     * 좌석 상태 실시간 폴링 API (개선된 버전)
     * - 좌석 상태 변경 시 즉시 응답, 변경사항 없으면 타임아웃까지 대기
     * - 클라이언트 정보 수집 및 오류 처리 강화
     *
     * @param concertId 콘서트 ID
     * @param lastUpdateTime 클라이언트가 마지막으로 받은 업데이트 시간 (선택적)
     * @param timeout 폴링 타임아웃 (ms, 기본 30초)
     * @param userId 사용자 ID (선택적)
     * @param request HTTP 요청 (User-Agent 등 추출용)
     * @return DeferredResult로 비동기 응답
     */
    @Operation(summary = "좌석 상태 실시간 폴링",
            description = "좌석 상태 변경 시 즉시 응답, 변경사항 없으면 최대 30초 대기")
    @GetMapping("/concerts/{concertId}/polling")
    public DeferredResult<ResponseEntity<?>> pollSeatUpdates(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,

            @Parameter(description = "마지막 업데이트 시간 (ISO 형식)", example = "2025-06-27T10:30:00")
            @RequestParam(required = false) String lastUpdateTime,

            @Parameter(description = "폴링 타임아웃 (밀리초)", example = "30000")
            @RequestParam(defaultValue = "30000") long timeout,

            @Parameter(description = "사용자 ID", example = "100")
            @RequestParam(required = false) Long userId,

            HttpServletRequest request) {

        // ✅ 개선: 입력 검증 강화
        if (concertId == null || concertId <= 0) {
            Map<String, Object> errorResponse = Map.of(
                    "hasUpdate", false,
                    "message", "유효하지 않은 콘서트 ID입니다."
            );
            DeferredResult<ResponseEntity<?>> errorResult = new DeferredResult<>();
            errorResult.setResult(ResponseEntity.badRequest()
                    .body(SuccessResponse.of("잘못된 요청", errorResponse)));
            return errorResult;
        }

        // ✅ 수정: 타임아웃 범위 검증 (final 변수로 생성)
        final long finalTimeout = Math.min(Math.max(timeout, MIN_TIMEOUT_MS), MAX_TIMEOUT_MS);

        // ✅ 개선: 클라이언트 정보 수집
        String userAgent = request.getHeader("User-Agent");
        String clientIp = getClientIpAddress(request);

        // DeferredResult 생성 (final 변수 사용)
        DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>(finalTimeout);

        try {
            // 세션 수 제한 확인
            if (sessionManager.getSessionCount(concertId) >= MAX_SESSIONS_PER_CONCERT) {
                Map<String, Object> overloadResponse = Map.of(
                        "hasUpdate", false,
                        "message", "서버 과부하로 인해 즉시 응답합니다. 잠시 후 다시 시도해주세요.",
                        "recommendedRetryAfter", 5000,
                        "currentLoad", sessionManager.getSessionCount(concertId),
                        "maxCapacity", MAX_SESSIONS_PER_CONCERT
                );
                deferredResult.setResult(ResponseEntity.status(503) // Service Unavailable
                        .body(SuccessResponse.of("서버 과부하", overloadResponse)));
                return deferredResult;
            }

            // ✅ 개선: 최근 업데이트 시간 파싱 및 즉시 응답 여부 확인
            LocalDateTime lastUpdate = parseLastUpdateTime(lastUpdateTime);
            if (lastUpdate != null && hasRecentUpdates(concertId, lastUpdate)) {
                // 최근 변경사항이 있으면 즉시 현재 상태 응답
                Map<String, Object> immediateResponse = getCurrentSeatStatusResponse(concertId);
                deferredResult.setResult(ResponseEntity.ok(SuccessResponse.of("즉시 응답", immediateResponse)));

                log.debug("즉시 응답 제공: concertId={}, userId={}, lastUpdate={}",
                        concertId, userId, lastUpdate);
                return deferredResult;
            }

            // ✅ 개선: 세션 등록 (User-Agent 포함)
            String sessionId = sessionManager.registerSession(concertId, deferredResult, userId, userAgent);
            if (sessionId == null) {
                // 세션 등록 실패 (서버 과부하)
                Map<String, Object> failResponse = Map.of(
                        "hasUpdate", false,
                        "message", "세션 등록 실패. 잠시 후 다시 시도해주세요.",
                        "errorCode", "SESSION_REGISTRATION_FAILED"
                );
                deferredResult.setResult(ResponseEntity.status(503)
                        .body(SuccessResponse.of("서비스 일시 불가", failResponse)));
                return deferredResult;
            }

            // ✅ 수정: 타임아웃 핸들러 설정 (final 변수 사용)
            deferredResult.onTimeout(() -> {
                Map<String, Object> timeoutResponse = Map.of(
                        "hasUpdate", false,
                        "message", "폴링 타임아웃",
                        "nextPollRecommended", true,
                        "timeoutMs", finalTimeout,
                        "serverTime", LocalDateTime.now()
                );
                deferredResult.setResult(ResponseEntity.ok(SuccessResponse.of("타임아웃", timeoutResponse)));

                log.debug("폴링 타임아웃: concertId={}, sessionId={}, userId={}, timeout={}ms",
                        concertId, sessionId, userId, finalTimeout);
            });

            // ✅ 개선: 에러 핸들러 설정
            deferredResult.onError(throwable -> {
                log.error("폴링 세션 에러: concertId={}, sessionId={}, userId={}, clientIp={}",
                        concertId, sessionId, userId, clientIp, throwable);

                Map<String, Object> errorResponse = Map.of(
                        "hasUpdate", false,
                        "message", "폴링 중 오류 발생",
                        "errorCode", "POLLING_ERROR"
                );
                deferredResult.setResult(ResponseEntity.status(500)
                        .body(SuccessResponse.of("서버 오류", errorResponse)));
            });

            // ✅ 개선: 완료 핸들러 설정 (로깅)
            deferredResult.onCompletion(() -> {
                log.debug("폴링 세션 완료: concertId={}, sessionId={}, userId={}",
                        concertId, sessionId, userId);
            });

            log.debug("폴링 세션 시작: concertId={}, sessionId={}, userId={}, timeout={}ms, clientIp={}",
                    concertId, sessionId, userId, finalTimeout, clientIp);

        } catch (Exception e) {
            log.error("폴링 요청 처리 중 오류: concertId={}, userId={}, clientIp={}",
                    concertId, userId, clientIp, e);

            Map<String, Object> errorResponse = Map.of(
                    "hasUpdate", false,
                    "message", "폴링 요청 처리 실패",
                    "errorCode", "REQUEST_PROCESSING_ERROR"
            );
            deferredResult.setResult(ResponseEntity.status(500)
                    .body(SuccessResponse.of("요청 처리 실패", errorResponse)));
        }

        return deferredResult;
    }

    /**
     * 폴링 시스템 상태 조회 (디버깅/모니터링용) - 개선된 버전
     */
    @Operation(summary = "폴링 시스템 상태 조회",
            description = "현재 활성 폴링 세션 정보 및 시스템 상태 조회")
    @GetMapping("/concerts/{concertId}/polling/status")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getPollingStatus(
            @PathVariable Long concertId) {

        try {
            Map<String, Object> status = Map.of(
                    "concertId", concertId,
                    "activeSessionCount", sessionManager.getSessionCount(concertId),
                    "totalActiveSessionCount", sessionManager.getTotalSessionCount(),
                    "activeConcertCount", sessionManager.getActiveConcertCount(),
                    "maxSessionsPerConcert", MAX_SESSIONS_PER_CONCERT,
                    "timeoutSettings", Map.of(
                            "default", DEFAULT_TIMEOUT_MS,
                            "min", MIN_TIMEOUT_MS,
                            "max", MAX_TIMEOUT_MS
                    ),
                    "serverTime", LocalDateTime.now(),
                    "systemStatus", sessionManager.getSystemStatus()
            );

            return ResponseEntity.ok(SuccessResponse.of("폴링 상태 조회 성공", status));

        } catch (Exception e) {
            log.error("폴링 상태 조회 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("폴링 상태 조회 실패", null));
        }
    }

    /**
     * 전체 시스템 통계 조회 (관리자용)
     */
    @Operation(summary = "폴링 시스템 전체 통계", description = "전체 시스템의 폴링 세션 통계 조회")
    @GetMapping("/polling/stats")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getPollingStats() {
        try {
            Map<String, Object> stats = Map.of(
                    "totalActiveSessions", sessionManager.getTotalSessionCount(),
                    "activeConcerts", sessionManager.getActiveConcertCount(),
                    "systemStatus", sessionManager.getSystemStatus(),
                    "serverInfo", Map.of(
                            "serverTime", LocalDateTime.now(),
                            "maxSessionsPerConcert", MAX_SESSIONS_PER_CONCERT,
                            "timeoutRange", MIN_TIMEOUT_MS + "ms - " + MAX_TIMEOUT_MS + "ms"
                    )
            );

            return ResponseEntity.ok(SuccessResponse.of("시스템 통계 조회 성공", stats));

        } catch (Exception e) {
            log.error("폴링 시스템 통계 조회 중 오류", e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("시스템 통계 조회 실패", null));
        }
    }

    /**
     * 마지막 업데이트 시간 파싱 (개선된 버전)
     */
    private LocalDateTime parseLastUpdateTime(String lastUpdateTime) {
        if (lastUpdateTime == null || lastUpdateTime.trim().isEmpty()) {
            return null;
        }

        try {
            // ISO Local DateTime 형식 파싱
            return LocalDateTime.parse(lastUpdateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("잘못된 시간 형식: {}, 오류: {}", lastUpdateTime, e.getMessage());
            return null;
        }
    }

    /**
     * 최근 업데이트 여부 확인 (개선된 구현 필요)
     * TODO: 실제로는 Redis에서 최근 변경 시간을 확인해야 함
     */
    private boolean hasRecentUpdates(Long concertId, LocalDateTime lastUpdate) {
        // 현재는 간단히 false 반환 (항상 Long Polling 수행)
        // 실제 구현에서는 Redis에 마지막 업데이트 시간을 저장하여 비교
        // 예: Redis에 "seat:last_update:{concertId}" 키로 마지막 업데이트 시간 저장
        return false;
    }

    /**
     * 현재 좌석 상태 응답 생성 (개선된 버전)
     */
    private Map<String, Object> getCurrentSeatStatusResponse(Long concertId) {
        // TODO: 실제로는 SeatStatusService에서 현재 상태 조회
        return Map.of(
                "hasUpdate", true,
                "updateTime", LocalDateTime.now(),
                "seatUpdates", Map.of(), // 실제로는 변경된 좌석 정보
                "message", "현재 상태 조회",
                "concertId", concertId
        );
    }

    /**
     * 클라이언트 IP 주소 추출 (프록시 고려)
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}