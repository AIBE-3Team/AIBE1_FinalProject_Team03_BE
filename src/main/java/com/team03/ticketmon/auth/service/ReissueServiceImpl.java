package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ReissueServiceImpl implements ReissueService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    @Override
    public String reissueToken(String refreshToken, String reissueCategory) {
        validateRefreshToken(refreshToken);

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String role = extractRole(refreshToken);

        return jwtTokenProvider.generateToken(reissueCategory, userId, role);
    }

    @Override
    public void handleReissueToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request);
        if (refreshToken == null || refreshToken.isEmpty())
            throw new IllegalArgumentException("Refresh Token이 존재하지 않습니다.");

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        if (refreshTokenService.findRefreshToken(userId, refreshToken).isEmpty())
            throw new IllegalArgumentException("Refresh Token이 유효하지 않습니다.");

        String newAccessToken = reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS);
        String newRefreshToken = reissueToken(refreshToken, jwtTokenProvider.CATEGORY_REFRESH);

        if(newAccessToken == null || newAccessToken.isEmpty())
            throw new IllegalArgumentException("Access Token 재발급이 실패했습니다.");
        if(newRefreshToken == null || newRefreshToken.isEmpty())
            throw new IllegalArgumentException("Refresh Token 재발급이 실패했습니다.");

        // 기존 Refresh Token 삭제 후 New Refresh Token DB 저장
        refreshTokenService.deleteRefreshToken(userId);
        refreshTokenService.saveRefreshToken(userId, newRefreshToken);

        // 새로운 토큰 쿠키에 추가
        Long accessCookieExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_ACCESS) / 1000;
        Long refreshCookieExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_REFRESH) / 1000;

        ResponseCookie accessCookie = cookieUtil.createCookie(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken, accessCookieExp);
        ResponseCookie refreshCookie = cookieUtil.createCookie(jwtTokenProvider.CATEGORY_REFRESH, newRefreshToken, refreshCookieExp);
        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
    }

    private void validateRefreshToken(String refreshToken) {
        String category = jwtTokenProvider.getCategory(refreshToken);
        if (!jwtTokenProvider.CATEGORY_REFRESH.equals(category))
            throw new IllegalArgumentException("유효하지 않은 카테고리 JWT 토큰입니다.");

        if (jwtTokenProvider.isTokenExpired(refreshToken))
            throw new IllegalArgumentException("Refresh Token이 만료되었습니다.");
    }

    private String extractRole(String token) {
        List<String> roles = jwtTokenProvider.getRoles(token);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("역할(Role) 정보가 없습니다.");
        }
        return roles.get(0);
    }
}
