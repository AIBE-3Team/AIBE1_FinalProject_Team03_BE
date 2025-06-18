package com.team03.ticketmon._global.config;

import com.team03.ticketmon.websocket.handler.CustomWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 관련 설정을 구성하는 클래스입니다.
 */
@Configuration
@EnableWebSocket // WebSocket 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CustomWebSocketHandler customWebSocketHandler;

    /**
     * WebSocket 핸들러를 특정 경로에 등록합니다.
     *
     * @param registry 핸들러를 등록할 레지스트리
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 클라이언트가 "/ws/waitqueue" 경로로 WebSocket 연결을 맺을 수 있도록 핸들러를 매핑합니다.
        registry.addHandler(customWebSocketHandler, "/ws/waitqueue")
                // TODO: [보안] 운영 환경에서는 CSRF 공격 등을 방지하기 위해 "*" 대신
                // 실제 서비스 도메인(예: "https://ticketmon.com")을 명시해야 합니다.
                .setAllowedOrigins("*");
    }
}