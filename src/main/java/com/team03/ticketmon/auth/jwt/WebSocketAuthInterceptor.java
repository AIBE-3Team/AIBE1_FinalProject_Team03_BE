package com.team03.ticketmon.auth.jwt;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        // 1. 쿠키에서 Access Token Get
        String accessToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_ACCESS, (HttpServletRequest) request);

        if (accessToken == null || jwtTokenProvider.isTokenExpired(accessToken)) {
            return false; // 핸드셰이크 실패, 연결 거부
        }

        // 2. 토큰이 유효하면, 사용자 ID 추출
        Long userId = jwtTokenProvider.getUserId(accessToken);

        // 3. WebSocket 세션의 attributes에 사용자 ID를 저장
        attributes.put("userId", userId);

        return true; // 핸드셰이크 성공, 연결 허용
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        log.info("WebSocket 핸드셰이크 완료");
    }
}