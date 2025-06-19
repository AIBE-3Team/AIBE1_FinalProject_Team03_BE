package com.team03.ticketmon.auth.Util;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    // 쿠키 생성
    public ResponseCookie createCookie(String key, String value, Long expiration) {
        return ResponseCookie.from(key, value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofMillis(expiration)) // 밀리초를 초로 변환
                .sameSite("Lax")
                .build();
    }

    // 쿠키 삭제
    public ResponseCookie deleteCookie(String key) {
        return ResponseCookie.from(key, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

}
