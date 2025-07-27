package com.team03.ticketmon.auth.jwt;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
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

        // 서블릿 기반 요청만 쿠키를 지원
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("비‐ 서블릿 요청이 수신되었고, WS 핸드셰이크를 거부했습니다");
            return false;
        }
        HttpServletRequest httpReq = servletRequest.getServletRequest();

        // 1. 쿠키에서 Access Token Get
        String accessToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_ACCESS, httpReq);

        if (accessToken == null || jwtTokenProvider.isTokenExpired(accessToken)) {
            log.warn("WebSocket handshake 거부: 유효하지 않은 Access Token");
            return false;
        }

        Long userId = jwtTokenProvider.getUserId(accessToken);
        attributes.put("userId", userId);

        // 2. 쿼리 파라미터에서 concertId 추출 (신규 로직)
        String concertIdStr = httpReq.getParameter("concertId");
        if (concertIdStr == null) {
            log.warn("WebSocket handshake 거부: concertId 파라미터가 없습니다.");
            return false;
        }

        try {
            Long concertId = Long.parseLong(concertIdStr);
            attributes.put("concertId", concertId);
        } catch (NumberFormatException e) {
            log.warn("WebSocket handshake 거부: 유효하지 않은 concertId 형식 - {}", concertIdStr);
            return false;
        }

        return true; // 핸드셰이크 성공, 연결 허용
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        log.info("WebSocket 핸드셰이크 완료");
    }
}