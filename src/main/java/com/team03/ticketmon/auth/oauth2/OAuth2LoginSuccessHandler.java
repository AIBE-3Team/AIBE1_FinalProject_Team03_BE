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
        String username = userEntity.getUsername();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities.isEmpty())
            throw new IllegalStateException("사용자 권한 정보가 없습니다.");
        
        GrantedAuthority grantedAuthority = authorities.iterator().next();
        String role = grantedAuthority.getAuthority();

        cookieUtil.generateAndSetJwtCookies(userId, username, role, response);

        response.sendRedirect("/");
    }
}
