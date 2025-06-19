package com.team03.ticketmon.auth.jwt;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    public LoginFilter(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider, RefreshTokenService refreshTokenService, CookieUtil cookieUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.cookieUtil = cookieUtil;
        setFilterProcessesUrl("/auth/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        // 클라이언트 요청에서 아이디, 비밀번호 추출
        String username = obtainUsername(request);
        String password = obtainPassword(request);

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password, null);

        return authenticationManager.authenticate(authToken);
    }

    // 로그인 성공 시 실행하는 메소드 (여기서 JWT를 발급)
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) {
        CustomUserDetails userDetails = (CustomUserDetails) authResult.getPrincipal();
        Long userId = userDetails.getUserId();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        String accessToken = jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_ACCESS, userId, role);
        String refreshToken = jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_REFRESH, userId, role);

        // Refresh Token DB 저장
        refreshTokenService.deleteRefreshToken(userId);
        refreshTokenService.saveRefreshToken(userId, refreshToken);

        Long accessCookieExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_ACCESS);
        Long refreshCookieExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_REFRESH);

        ResponseCookie accessCookie = cookieUtil.createCookie(jwtTokenProvider.CATEGORY_ACCESS, accessToken, accessCookieExp);
        ResponseCookie refreshCookie = cookieUtil.createCookie(jwtTokenProvider.CATEGORY_REFRESH, refreshToken, refreshCookieExp);
        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
        response.setStatus(HttpServletResponse.SC_OK);
    }

    // 로그인 실패시 실행하는 메소드
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        response.sendRedirect("/auth/login.html?error");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
