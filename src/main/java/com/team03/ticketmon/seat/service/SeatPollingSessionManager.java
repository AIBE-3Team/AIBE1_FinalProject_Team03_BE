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
 * 좌석 폴링 세션 관리 컴포넌트
 * - 활성 Long Polling 세션들을 콘서트별로 관리
 * - 이벤트 수신 시 해당 콘서트의 모든 대기 세션에 응답
 * - 메모리 누수 방지를 위한 자동 정리 기능
 */
@Slf4j
@Component
public class SeatPollingSessionManager {

    // 콘서트별 활성 세션 관리: concertId -> List<PollingSession>
    private final Map<Long, List<PollingSession>> activeSessions = new ConcurrentHashMap<>();

    // 세션 ID 생성기
    private final AtomicLong sessionIdGenerator = new AtomicLong(0);

    // 설정값
    private static final int MAX_SESSIONS_PER_CONCERT = 1000; // 콘서트당 최대 세션 수
    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30초 기본 타임아웃

    /**
     * 폴링 세션 정보를 담는 내부 클래스
     */
    public static class PollingSession {
        private final String sessionId;
        private final DeferredResult<ResponseEntity<?>> deferredResult;
        private final LocalDateTime startTime;
        private final Long userId; // 선택적

        public PollingSession(String sessionId, DeferredResult<ResponseEntity<?>> deferredResult, Long userId) {
            this.sessionId = sessionId;
            this.deferredResult = deferredResult;
            this.startTime = LocalDateTime.now();
            this.userId = userId;
        }

        // Getters
        public String getSessionId() { return sessionId; }
        public DeferredResult<ResponseEntity<?>> getDeferredResult() { return deferredResult; }
        public LocalDateTime getStartTime() { return startTime; }
        public Long getUserId() { return userId; }
    }

    /**
     * 새로운 폴링 세션 등록
     *
     * @param concertId 콘서트 ID
     * @param deferredResult DeferredResult 객체
     * @param userId 사용자 ID (선택적)
     * @return 등록된 세션 ID
     */
    public String registerSession(Long concertId, DeferredResult<ResponseEntity<?>> deferredResult, Long userId) {
        // 세션 수 제한 확인
        if (getSessionCount(concertId) >= MAX_SESSIONS_PER_CONCERT) {
            log.warn("콘서트 최대 세션 수 초과: concertId={}, currentCount={}",
                    concertId, getSessionCount(concertId));
            return null;
        }

        String sessionId = generateSessionId();
        PollingSession session = new PollingSession(sessionId, deferredResult, userId);

        // 콘서트별 세션 리스트에 추가
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
     * 특정 콘서트의 모든 대기 세션에 이벤트 알림
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

        for (PollingSession session : sessionsCopy) {
            try {
                DeferredResult<ResponseEntity<?>> deferredResult = session.getDeferredResult();

                if (!deferredResult.isSetOrExpired()) {
                    // 응답 데이터 구성
                    Map<String, Object> response = createEventResponse(event);
                    deferredResult.setResult(ResponseEntity.ok(response));
                    notifiedCount++;
                }
            } catch (Exception e) {
                log.warn("세션 알림 실패: concertId={}, sessionId={}",
                        concertId, session.getSessionId(), e);
            }
        }

        log.info("좌석 업데이트 알림 완료: concertId={}, seatId={}, notifiedSessions={}/{}",
                concertId, event.seatId(), notifiedCount, sessionsCopy.size());
    }

    /**
     * 세션 제거
     *
     * @param concertId 콘서트 ID
     * @param sessionId 세션 ID
     */
    public void removeSession(Long concertId, String sessionId) {
        List<PollingSession> sessions = activeSessions.get(concertId);
        if (sessions != null) {
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
     * 만료된 세션들 정리 (스케줄러에서 호출)
     */
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        int cleanedCount = 0;

        for (Map.Entry<Long, List<PollingSession>> entry : activeSessions.entrySet()) {
            Long concertId = entry.getKey();
            List<PollingSession> sessions = entry.getValue();

            Iterator<PollingSession> iterator = sessions.iterator();
            while (iterator.hasNext()) {
                PollingSession session = iterator.next();

                // 5분 이상 된 세션은 정리
                if (session.getStartTime().plusMinutes(5).isBefore(now) ||
                        session.getDeferredResult().isSetOrExpired()) {
                    iterator.remove();
                    cleanedCount++;
                }
            }

            // 빈 리스트이면 맵에서 제거
            if (sessions.isEmpty()) {
                activeSessions.remove(concertId);
            }
        }

        if (cleanedCount > 0) {
            log.info("만료된 폴링 세션 정리 완료: cleanedCount={}, activeConcerts={}",
                    cleanedCount, activeSessions.size());
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
     * 이벤트 응답 데이터 구성
     */
    private Map<String, Object> createEventResponse(SeatUpdateEvent event) {
        return Map.of(
                "hasUpdate", true,
                "updateTime", event.timestamp(),
                "seatUpdates", List.of(
                        Map.of(
                                "seatId", event.seatId(),
                                "status", event.status().toString(),
                                "userId", event.userId(),
                                "seatInfo", event.seatInfo()
                        )
                )
        );
    }

    /**
     * 고유 세션 ID 생성
     */
    private String generateSessionId() {
        return "session-" + sessionIdGenerator.incrementAndGet() + "-" + System.currentTimeMillis();
    }
}