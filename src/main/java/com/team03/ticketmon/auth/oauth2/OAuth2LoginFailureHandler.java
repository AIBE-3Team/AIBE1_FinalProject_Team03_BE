package com.team03.ticketmon.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String targeUrl;
    private String REGISTER_URL = "/register";
    private String LOGIN_URL = "/login";
    private final String NEED_SIGNUP_ERROR_CODE = "need_signup";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        if (exception instanceof OAuth2AuthenticationException) {
            OAuth2AuthenticationException ex = (OAuth2AuthenticationException) exception;

            if (NEED_SIGNUP_ERROR_CODE.equals(ex.getError().getErrorCode())) {
                String registerUrl = UriComponentsBuilder
                        .fromUriString(targeUrl + REGISTER_URL)
                        .queryParam("error", NEED_SIGNUP_ERROR_CODE)
                        .build().toUriString();

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.sendRedirect(registerUrl);
                return;
            }
        }

        // 기본 실패 처리
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.sendRedirect(targeUrl + LOGIN_URL);
    }
}
