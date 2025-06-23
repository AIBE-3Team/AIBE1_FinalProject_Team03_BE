package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
    public String reissueToken(String refreshToken, String reissueCategory, boolean dbCheck) {
        refreshTokenService.validateRefreshToken(refreshToken, dbCheck);
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        String role = extractRole(refreshToken);
        return jwtTokenProvider.generateToken(reissueCategory, userId, username, role);
    }

    @Override
    public void handleReissueToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request);
        if (refreshToken == null || refreshToken.isEmpty())
            throw new IllegalArgumentException("Refresh Token이 존재하지 않습니다.");

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        String role = extractRole(refreshToken);

        refreshTokenService.validateRefreshToken(refreshToken, true);

        cookieUtil.generateAndSetJwtCookies(userId, username, role, response);

        response.setStatus(HttpServletResponse.SC_OK);
    }

    private String extractRole(String token) {
        List<String> roles = jwtTokenProvider.getRoles(token);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("역할(Role) 정보가 없습니다.");
        }
        return roles.get(0);
    }
}
