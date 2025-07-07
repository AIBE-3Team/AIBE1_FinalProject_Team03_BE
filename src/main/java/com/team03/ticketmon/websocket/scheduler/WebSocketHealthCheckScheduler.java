package com.team03.ticketmon.websocket.scheduler;

import com.team03.ticketmon.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 WebSocket 연결 현황을 로깅하여 모니터링하는 스케줄러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHealthCheckScheduler {

    private final WebSocketSessionManager sessionManager;

    /**
     * 주기적으로 현재 활성 WebSocket 연결 수를 로깅합니다.
     */
    @Scheduled(fixedDelayString = "${app.websocket.scheduler-health.delay-ms:60000}")
    public void logWebSocketStatus() {
        int sessionCount = sessionManager.getSessionCount();
        if (sessionCount > 0) {
            log.debug("[WebSocket-Status] 현재 활성 WebSocket 연결 수: {}", sessionCount);
            log.debug("[WebSocket-Status] 연결된 사용자 목록: {}", sessionManager.getConnectedUsers());
        } else {
            log.debug("[WebSocket-Status] 현재 활성 WebSocket 연결이 없습니다.");
        }
    }
}