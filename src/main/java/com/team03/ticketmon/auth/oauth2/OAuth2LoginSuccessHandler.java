package com.team03.ticketmon.auth.oauth2;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.service.UserEntityService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Collection;

@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserEntityService userEntityService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");

        UserEntity userEntity = userEntityService.findUserEntityByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("소셜 로그인 유저의 이메일 정보가 없습니다."));

        Long userId = userEntity.getId();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities.isEmpty())
            throw new IllegalStateException("사용자 권한 정보가 없습니다.");
        
        GrantedAuthority grantedAuthority = authorities.iterator().next();
        String role = grantedAuthority.getAuthority();

        String accessToken = jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_ACCESS, userId, role);
        String refreshToken = jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_REFRESH, userId, role);

        // Refresh Token DB 저장
        refreshTokenService.deleteRefreshToken(userId);
        refreshTokenService.saveRefreshToken(userId, refreshToken);

        addCookie(response, accessToken, refreshToken);

        response.sendRedirect("/");
    }

    private void addCookie(HttpServletResponse response, String accessToken, String refreshToken) {
        Long accessCookieExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_ACCESS);
        Long refreshCookieExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_REFRESH);

        ResponseCookie accessCookie = cookieUtil.createCookie(jwtTokenProvider.CATEGORY_ACCESS, accessToken, accessCookieExp);
        ResponseCookie refreshCookie = cookieUtil.createCookie(jwtTokenProvider.CATEGORY_REFRESH, refreshToken, refreshCookieExp);
        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
    }
}
