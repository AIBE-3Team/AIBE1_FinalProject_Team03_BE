package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReissueServiceImpl implements ReissueService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

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
        String newAccessToken = reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS);
        String newRefreshToken = reissueToken(refreshToken, jwtTokenProvider.CATEGORY_REFRESH);

        // 기존 Refresh Token 삭제 후 New Refresh Token DB 저장
        refreshTokenService.deleteRefreshToken(userId);
        refreshTokenService.saveRefreshToken(userId, refreshToken);

        // 새로운 토큰 쿠키에 추가
        response.addCookie(jwtTokenProvider.createCookie(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken));
        response.addCookie(jwtTokenProvider.createCookie(jwtTokenProvider.CATEGORY_REFRESH, newRefreshToken));
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
