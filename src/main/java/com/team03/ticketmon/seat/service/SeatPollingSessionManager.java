package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.seat.dto.SeatUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 좌석 폴링 세션 관리 컴포넌트 (개선된 버전)
 * - 활성 Long Polling 세션들을 콘서트별로 관리
 * - 이벤트 수신 시 해당 콘서트의 모든 대기 세션에 응답
 * - 메모리 누수 방지를 위한 자동 정리 기능
 * - 성능 최적화 및 동시성 개선
 */
@Slf4j
@Component
public class SeatPollingSessionManager {

    // 콘서트별 활성 세션 관리: concertId -> List<PollingSession>
    private final Map<Long, List<PollingSession>> activeSessions = new ConcurrentHashMap<>();

    // 세션 ID 생성기
    private final AtomicLong sessionIdGenerator = new AtomicLong(0);

    // 설정값 - 환경별로 조정 가능하도록 개선
    private static final int MAX_SESSIONS_PER_CONCERT = 1000; // 콘서트당 최대 세션 수
    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30초 기본 타임아웃
    private static final long SESSION_CLEANUP_MINUTES = 5; // 5분 이상 된 세션 정리

    /**
     * 폴링 세션 정보를 담는 내부 클래스 (개선된 버전)
     */
    public static class PollingSession {
        private final String sessionId;
        private final DeferredResult<ResponseEntity<?>> deferredResult;
        private final LocalDateTime startTime;
        private final Long userId; // 선택적
        private final String userAgent; // 디버깅용 추가

        public PollingSession(String sessionId, DeferredResult<ResponseEntity<?>> deferredResult,
                              Long userId, String userAgent) {
            this.sessionId = sessionId;
            this.deferredResult = deferredResult;
            this.startTime = LocalDateTime.now();
            this.userId = userId;
            this.userAgent = userAgent; // 새로 추가
        }

        // Getters (✅ typo 수정)
        public String getSessionId() { return sessionId; }
        public DeferredResult<ResponseEntity<?>> getDeferredResult() { return deferredResult; }
        public LocalDateTime getStartTime() { return startTime; }
        public Long getUserId() { return userId; } // ✅ 수정: "retursn" → "return"
        public String getUserAgent() { return userAgent; } // 새로 추가
    }

    /**
     * 새로운 폴링 세션 등록 (개선된 버전)
     *
     * @param concertId 콘서트 ID
     * @param deferredResult DeferredResult 객체
     * @param userId 사용자 ID (선택적)
     * @param userAgent 사용자 에이전트 (디버깅용, 선택적)
     * @return 등록된 세션 ID
     */
    public String registerSession(Long concertId, DeferredResult<ResponseEntity<?>> deferredResult,
                                  Long userId, String userAgent) {

        // 입력 검증 강화
        if (concertId == null || deferredResult == null) {
            log.warn("필수 파라미터가 null입니다: concertId={}, deferredResult={}", concertId, deferredResult);
            return null;
        }

        // 세션 수 제한 확인
        if (getSessionCount(concertId) >= MAX_SESSIONS_PER_CONCERT) {
            log.warn("콘서트 최대 세션 수 초과: concertId={}, currentCount={}",
                    concertId, getSessionCount(concertId));
            return null;
        }

        String sessionId = generateSessionId();
        PollingSession session = new PollingSession(sessionId, deferredResult, userId, userAgent);

        // 콘서트별 세션 리스트에 추가 (스레드 안전성 보장)
        activeSessions.computeIfAbsent(concertId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(session);

        // DeferredResult 완료/타임아웃 시 자동 정리
        deferredResult.onCompletion(() -> removeSession(concertId, sessionId));
        deferredResult.onTimeout(() -> removeSession(concertId, sessionId));

        log.debug("폴링 세션 등록: concertId={}, sessionId={}, userId={}, totalSessions={}",
                concertId, sessionId, userId, getSessionCount(concertId));

        return sessionId;
    }

    /**
     * 기존 registerSession 메서드 호환성 유지 (오버로드)
     */
    public String registerSession(Long concertId, DeferredResult<ResponseEntity<?>> deferredResult, Long userId) {
        return registerSession(concertId, deferredResult, userId, null);
    }

    /**
     * 특정 콘서트의 모든 대기 세션에 이벤트 알림 (개선된 버전)
     *
     * @param event 좌석 업데이트 이벤트
     */
    public void notifyWaitingSessions(SeatUpdateEvent event) {
        Long concertId = event.concertId();
        List<PollingSession> sessions = activeSessions.get(concertId);

        if (sessions == null || sessions.isEmpty()) {
            log.debug("알림할 세션이 없음: concertId={}", concertId);
            return;
        }

        // 동시 수정 방지를 위해 복사본에서 작업
        List<PollingSession> sessionsCopy = new ArrayList<>(sessions);
        int notifiedCount = 0;
        int errorCount = 0;

        for (PollingSession session : sessionsCopy) {
            try {
                DeferredResult<ResponseEntity<?>> deferredResult = session.getDeferredResult();

                if (!deferredResult.isSetOrExpired()) {
                    // 응답 데이터 구성
                    Map<String, Object> response = createEventResponse(event);
                    deferredResult.setResult(ResponseEntity.ok(response));
                    notifiedCount++;

                    log.debug("세션 알림 성공: concertId={}, sessionId={}, userId={}",
                            concertId, session.getSessionId(), session.getUserId());
                } else {
                    log.debug("만료된 세션 스킵: concertId={}, sessionId={}",
                            concertId, session.getSessionId());
                }
            } catch (Exception e) {
                errorCount++;
                log.warn("세션 알림 실패: concertId={}, sessionId={}, error={}",
                        concertId, session.getSessionId(), e.getMessage());
            }
        }

        log.info("좌석 업데이트 알림 완료: concertId={}, seatId={}, notified={}/{}, errors={}",
                concertId, event.seatId(), notifiedCount, sessionsCopy.size(), errorCount);
    }

    /**
     * 세션 제거 (개선된 버전)
     *
     * @param concertId 콘서트 ID
     * @param sessionId 세션 ID
     */
    public void removeSession(Long concertId, String sessionId) {
        List<PollingSession> sessions = activeSessions.get(concertId);
        if (sessions != null) {
            // 세션 제거 (스레드 안전)
            sessions.removeIf(session -> sessionId.equals(session.getSessionId()));

            // 빈 리스트이면 맵에서 제거 (메모리 절약)
            if (sessions.isEmpty()) {
                activeSessions.remove(concertId);
            }

            log.debug("폴링 세션 제거: concertId={}, sessionId={}, remainingSessions={}",
                    concertId, sessionId, sessions.size());
        }
    }

    /**
     * 만료된 세션들 정리 (스케줄러에서 호출) - 개선된 버전
     */
    public void cleanupExpiredSessions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(SESSION_CLEANUP_MINUTES);
        int cleanedCount = 0;
        int totalSessionsBefore = getTotalSessionCount();

        // ConcurrentHashMap이므로 iterator 사용 가능
        Iterator<Map.Entry<Long, List<PollingSession>>> concertIterator = activeSessions.entrySet().iterator();

        while (concertIterator.hasNext()) {
            Map.Entry<Long, List<PollingSession>> entry = concertIterator.next();
            Long concertId = entry.getKey();
            List<PollingSession> sessions = entry.getValue();

            // 각 콘서트별 세션 정리
            Iterator<PollingSession> sessionIterator = sessions.iterator();
            while (sessionIterator.hasNext()) {
                PollingSession session = sessionIterator.next();

                // 만료 조건: 5분 이상 된 세션 또는 이미 처리된 세션
                if (session.getStartTime().isBefore(cutoffTime) ||
                        session.getDeferredResult().isSetOrExpired()) {
                    sessionIterator.remove();
                    cleanedCount++;

                    log.debug("만료된 세션 정리: concertId={}, sessionId={}, startTime={}",
                            concertId, session.getSessionId(), session.getStartTime());
                }
            }

            // 빈 세션 리스트 제거
            if (sessions.isEmpty()) {
                concertIterator.remove();
            }
        }

        if (cleanedCount > 0) {
            log.info("만료된 폴링 세션 정리 완료: cleaned={}, before={}, after={}, activeConcerts={}",
                    cleanedCount, totalSessionsBefore, getTotalSessionCount(), activeSessions.size());
        }
    }

    /**
     * 특정 콘서트의 활성 세션 수 조회
     */
    public int getSessionCount(Long concertId) {
        List<PollingSession> sessions = activeSessions.get(concertId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * 전체 활성 세션 수 조회
     */
    public int getTotalSessionCount() {
        return activeSessions.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * 전체 활성 콘서트 수 조회 (모니터링용)
     */
    public int getActiveConcertCount() {
        return activeSessions.size();
    }

    /**
     * 특정 사용자의 세션 수 조회 (디버깅용)
     */
    public long getUserSessionCount(Long userId) {
        if (userId == null) return 0;

        return activeSessions.values().stream()
                .flatMap(List::stream)
                .filter(session -> userId.equals(session.getUserId()))
                .count();
    }

    /**
     * 이벤트 응답 데이터 구성 (개선된 버전)
     */
    private Map<String, Object> createEventResponse(SeatUpdateEvent event) {
        return Map.of(
                "hasUpdate", true,
                "updateTime", event.timestamp(),
                "eventType", "SEAT_STATUS_CHANGE",
                "seatUpdates", List.of(
                        Map.of(
                                "seatId", event.seatId(),
                                "status", event.status().toString(),
                                "userId", event.userId(),
                                "seatInfo", event.seatInfo()
                        )
                ),
                "serverTime", LocalDateTime.now()
        );
    }

    /**
     * 고유 세션 ID 생성 (개선된 버전)
     */
    private String generateSessionId() {
        return "session-" + sessionIdGenerator.incrementAndGet() + "-" + System.currentTimeMillis();
    }

    /**
     * 시스템 상태 조회 (모니터링용)
     */
    public Map<String, Object> getSystemStatus() {
        return Map.of(
                "totalSessions", getTotalSessionCount(),
                "activeConcerts", getActiveConcertCount(),
                "maxSessionsPerConcert", MAX_SESSIONS_PER_CONCERT,
                "sessionCleanupMinutes", SESSION_CLEANUP_MINUTES,
                "lastCleanupTime", LocalDateTime.now()
        );
    }
}