package com.team03.ticketmon.auth.Util;

import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

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

    // Access Token과 Refresh Token 쿠키 삭제 (로그아웃)
    public void deleteJwtCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = deleteCookie(jwtTokenProvider.CATEGORY_ACCESS);
        ResponseCookie refreshCookie = deleteCookie(jwtTokenProvider.CATEGORY_REFRESH);

        addJwtCookiesToResponse(accessCookie, refreshCookie, response);
    }

    // Access Token과 Refresh Token 쿠키 생성
    public void generateAndSetJwtCookies(Long userId, String username, String role, HttpServletResponse response) {
        String newAccessToken = generateAccessToken(userId, username, role);
        String newRefreshToken = generateRefreshTokenAndSave(userId, username, role);

        if(newAccessToken == null || newAccessToken.isEmpty())
            throw new IllegalArgumentException("Access Token 재발급이 실패했습니다.");
        if(newRefreshToken == null || newRefreshToken.isEmpty())
            throw new IllegalArgumentException("Refresh Token 재발급이 실패했습니다.");

        ResponseCookie accessCookie = createJwtCookie(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken);
        ResponseCookie refreshCookie = createJwtCookie(jwtTokenProvider.CATEGORY_REFRESH, newRefreshToken);

        addJwtCookiesToResponse(accessCookie, refreshCookie, response);
    }

    private String generateAccessToken(Long userId, String username, String role) {
        return jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_ACCESS, userId, username, role);
    }

    private String generateRefreshTokenAndSave(Long userId, String username, String role) {
        String newRefreshToken = jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_REFRESH, userId, username, role);
        refreshTokenService.deleteRefreshToken(userId);
        refreshTokenService.saveRefreshToken(userId, newRefreshToken);
        return newRefreshToken;
    }

    private ResponseCookie createJwtCookie(String category, String token) {
        Long expiration = jwtTokenProvider.getExpirationMs(category);
        return createCookie(category, token, expiration);
    }

    private void addJwtCookiesToResponse(ResponseCookie accessCookie, ResponseCookie refreshCookie, HttpServletResponse response) {
        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
    }
}
