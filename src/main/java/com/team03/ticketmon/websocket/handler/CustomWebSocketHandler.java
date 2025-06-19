package com.team03.ticketmon.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomWebSocketHandler extends TextWebSocketHandler {

    // 동시성 이슈를 방지 목적, 스레드에 안전한 콜렉션 사용
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * 클라이언트와 WebSocket 연결이 성공적으로 맺어졌을 때 호출
     *
     * @param session 새로 연결된 클라이언트의 세션
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
        if (userId != null) {
            sessions.put(userId, session);
            log.info("WebSocket 연결됨. 사용자: {}, 세션 ID: {}", userId, session.getId());
        } else {
            // WARN 레벨: 비정상적인 접근 시도일 수 있으므로 경고 로그
            log.warn("사용자 ID 없이 WebSocket 연결 시도됨. 세션 ID: {}, URI: {}", session.getId(), session.getUri());
        }
    }

    /**
     * 텍스트 메시지를 수신했을 때 호출됩니다.
     * 현재 시스템에서는 클라이언트로부터 메시지를 받는 로직은 없지만, 향후 확장을 위해 남겨둡니다.
     * (예: 클라이언트가 "ping"을 보내 연결을 유지하는 로직)
     * * @param session 메시지를 보낸 클라이언트의 세션
     * @param message 수신된 텍스트 메시지
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = extractUserId(session);
//        log.debug("메시지 수신. 사용자: {}, 내용: {}", userId, message.getPayload());
        log.info("메시지 수신. 사용자: {}, 내용: {}", userId, message.getPayload());
        // 여기에 클라이언트가 보낸 메시지를 처리하는 로직 추가 가능
    }

    /**
     * 클라이언트와 WebSocket 연결이 끊어졌을 때 호출됩니다.
     *
     * @param session 연결이 끊어진 클라이언트의 세션
     * @param status  연결 종료 상태 (코드 및 원인)
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = extractUserId(session);
        if (userId != null) {
            sessions.remove(userId);
            log.info("WebSocket 연결 종료됨. 사용자: {}, 세션 ID: {}, 상태: {}", userId, session.getId(), status);
        }
    }

    /**
     * 특정 사용자에게 메시지(Payload)를 전송
     *
     * @param userId  메시지를 받을 사용자 ID
     * @param payload 전송할 데이터 (Map 형태, JSON으로 변환됨)
     */
    public void sendMessageToUser(String userId, Map<String, Object> payload) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String message = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(message));
                log.info("메시지 전송 성공. 사용자: {}, 내용: {}", userId, message);
            } catch (IOException e) {
                log.error("WebSocket 메시지 전송 실패! 사용자: {}", userId, e);
            }
        } else {
            // WARN 레벨: 알림을 보내야 할 사용자의 세션이 없는 경우, 중요한 이벤트 누락 가능성
            log.warn("메시지를 전송할 수 없음. 세션이 존재하지 않거나 닫혀있음. 사용자: {}", userId);
        }
    }


    /**
     * 사용자 ID를 추출
     *
     * @param session 클라이언트 세션
     * @return 추출된 사용자 ID, 없으면 null
     */
    private String extractUserId(WebSocketSession session) {
        // WebSocket Handshake 과정에서 인터셉터를 통해 JWT 같은 인증 토큰을 검증하고 사용자 ID를 추출해야 합니다.
        Map<String, Object> attributes = session.getAttributes();
        return (String) attributes.get("userId");
    }

    // 테스트와 외부에서 세션을 추가하기 위한 public 메서드
    public void addSession(String userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public void sendMessage(WebSocketSession session, TextMessage message) throws IOException {
        session.sendMessage(message);
    }
}