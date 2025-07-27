package com.team03.ticketmon.auth.jwt;

import jakarta.servlet.http.Cookie;
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

        // 디버깅: 모든 쿠키 출력
        log.debug("=== WebSocket 핸드셰이크 시작 ===");
        log.debug("요청 URL: {}", httpReq.getRequestURL());
        log.debug("쿼리 스트링: {}", httpReq.getQueryString());

        Cookie[] cookies = httpReq.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                log.debug("쿠키 - name: {}, value: {}", cookie.getName(), cookie.getValue());
            }
        } else {
            log.debug("쿠키가 없습니다!");
        }

        // 1. 쿠키에서 Access Token Get
        String accessToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_ACCESS, httpReq);
        log.debug("추출된 Access Token: {}", accessToken != null ? "존재함" : "null");

        if (accessToken == null) {
            log.warn("WebSocket handshake 거부: Access Token이 없습니다");
            return false;
        }

        if (jwtTokenProvider.isTokenExpired(accessToken)) {
            log.warn("WebSocket handshake 거부: Access Token이 만료되었습니다");
            return false;
        }

        Long userId = jwtTokenProvider.getUserId(accessToken);
        log.debug("추출된 userId: {}", userId);
        attributes.put("userId", userId);

        // 2. 쿼리 파라미터에서 concertId 추출 (신규 로직)
        String concertIdStr = httpReq.getParameter("concertId");
        log.debug("concertId 파라미터: {}", concertIdStr);

        if (concertIdStr == null) {
            log.warn("WebSocket handshake 거부: concertId 파라미터가 없습니다.");
            return false;
        }

        try {
            Long concertId = Long.parseLong(concertIdStr);
            attributes.put("concertId", concertId);
            log.info("WebSocket 핸드셰이크 성공 - userId: {}, concertId: {}", userId, concertId);
            return true;
        } catch (NumberFormatException e) {
            log.warn("WebSocket handshake 거부: 유효하지 않은 concertId 형식 - {}", concertIdStr);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket 핸드셰이크 후 에러 발생", exception);
        } else {
            log.info("WebSocket 핸드셰이크 완료");
        }
    }
}