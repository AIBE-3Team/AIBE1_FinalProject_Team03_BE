package com.team03.ticketmon.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final String NEED_SIGNUP_ERROR_CODE = "need_signup";
    private final String REGISTER_URL = "/auth/register";
    private final String LOGIN_ERROR_URL = "/auth/login?error";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof OAuth2AuthenticationException) {
            OAuth2AuthenticationException ex = (OAuth2AuthenticationException) exception;
            if (NEED_SIGNUP_ERROR_CODE.equals(ex.getError().getErrorCode())) {
                response.sendRedirect(REGISTER_URL);
                return;
            }
        }
        // 기본 실패 처리
        response.sendRedirect(LOGIN_ERROR_URL);
    }
}
