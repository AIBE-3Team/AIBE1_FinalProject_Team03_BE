package com.team03.ticketmon.support;

import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

public class WithMockCustomUserSecurityContextFactory implements WithSecurityContextFactory<WithMockCustomUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockCustomUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + annotation.role()));

        // 테스트용 CustomUserDetails 객체 생성
        CustomUserDetails customUserDetails = new CustomUserDetails(
                annotation.userId(),
                "testuser", // username은 테스트에서 중요하지 않다면 고정값 사용
                "testnickname",
                "",       // password는 사용하지 않으므로 비워둠
                authorities
        );

        // Authentication 객체를 만들어 SecurityContext에 설정
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                customUserDetails,
                null,
                authorities
        );
        context.setAuthentication(authentication);
        return context;
    }
}