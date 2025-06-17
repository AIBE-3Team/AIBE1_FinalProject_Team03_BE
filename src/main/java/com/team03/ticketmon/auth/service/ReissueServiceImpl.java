package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReissueServiceImpl implements ReissueService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    public String reissueAccessToken(String refreshToken) {
        String category = jwtTokenProvider.getCategory(refreshToken);
        if (!jwtTokenProvider.CATEGORY_REFRESH.equals(category)) {
            throw new IllegalArgumentException("유효하지 않은 카테고리 JWT 토큰입니다.");
        }

        if (!jwtTokenProvider.isTokenExpired(refreshToken))
            return null;

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String role = jwtTokenProvider.getRoles(refreshToken).get(0);

        return jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_ACCESS, userId, role);
    }

    @Override
    public String reissueRefreshToken(String refreshToken) {
        if (!jwtTokenProvider.isTokenExpired(refreshToken))
            return null;

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String role = jwtTokenProvider.getRoles(refreshToken).get(0);

        return jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_REFRESH, userId, role);
    }

    @Override
    public void handleReissueToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request);
        if (refreshToken == null || refreshToken.isEmpty())
            throw new IllegalArgumentException("Refresh Token이 존재하지 않습니다.");

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String newAccessToken = reissueAccessToken(refreshToken);
        String newRefreshToken = reissueRefreshToken(refreshToken);

        if (newAccessToken == null || newRefreshToken == null)
            throw new IllegalArgumentException("Token 재발급이 실패했습니다.");

        // 기존 Refresh Token 삭제 후 New Refresh Token DB 저장
        refreshTokenService.deleteRefreshToken(userId);
        refreshTokenService.saveRefreshToken(userId, refreshToken);

        // 새로운 토큰 쿠키에 추가
        response.addCookie(jwtTokenProvider.createCookie(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken));
        response.addCookie(jwtTokenProvider.createCookie(jwtTokenProvider.CATEGORY_REFRESH, newRefreshToken));
    }
}
