package com.team03.ticketmon.auth.jwt;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.service.ReissueService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final ReissueService reissueService;
    private final CookieUtil cookieUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String accessToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_ACCESS, request);
        String refreshToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request);

        if (isEmpty(refreshToken)) {
            if (!isEmpty(accessToken)) {
                // Refresh Token이 없고 Access Token만 남아 있으면 삭제
                ResponseCookie accessCookie = cookieUtil.deleteCookie(jwtTokenProvider.CATEGORY_ACCESS);
                response.addHeader("Set-Cookie", accessCookie.toString());
            }

            filterChain.doFilter(request, response);
            return;
        }

        // Access Token이 유효하다면 그대로 인증 처리
        if (!isEmpty(accessToken) && !jwtTokenProvider.isTokenExpired(accessToken)) {
            setAuthenticationContext(accessToken);
            filterChain.doFilter(request, response);
            return;
        }

        // Access Token이 만료되었고 Refresh Token 유효성 확인 후 재발급
        accessToken = handleTokenReissue(refreshToken, response);

        if (isEmpty(accessToken)) return;

        if (!isAccessToken(accessToken, response)) return;

        setAuthenticationContext(accessToken);
        filterChain.doFilter(request, response);
    }

    private String handleTokenReissue(String refreshToken, HttpServletResponse response) throws IOException {
        log.info("Access Token 만료됨 Refresh Token으로 재발급 시도");

        try {
            String newAccessToken = reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, true);

            if (isEmpty(newAccessToken)) {
                log.warn("Refresh Token이 유효하지 않음 재로그인 필요");
                cookieUtil.deleteJwtCookies(response);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh Token이 만료되었거나 유효하지 않습니다.");
                return null;
            }

            Long accessExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_ACCESS);
            ResponseCookie accessCookie = cookieUtil.createCookie(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken, accessExp);
            response.addHeader("Set-Cookie", accessCookie.toString());
            return newAccessToken;

        } catch (Exception e) {
            log.warn("Access Token 재발급 실패: {}", e.getMessage());
            cookieUtil.deleteJwtCookies(response);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh Token이 만료되었거나 유효하지 않습니다.");
            return null;
        }
    }

    private boolean isAccessToken(String token, HttpServletResponse response) throws IOException {
        try {
            String category = jwtTokenProvider.getCategory(token);
            return jwtTokenProvider.CATEGORY_ACCESS.equals(category);
        } catch (Exception e) {
            log.error("Access Token 파싱 실패 : {}", e.getMessage());
            cookieUtil.deleteJwtCookies(response);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access Token 파싱에 실패했습니다.");
            return false;
        }
    }

    // 인증 객체 등록
    private void setAuthenticationContext(String token) {
        Authentication auth = jwtTokenProvider.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
}