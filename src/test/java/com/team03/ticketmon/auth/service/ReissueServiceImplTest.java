package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT 토큰 재발급 테스트")
class ReissueServiceImplTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    HttpServletRequest request;
    @Mock
    HttpServletResponse response;

    private ReissueServiceImpl reissueService;
    private CookieUtil cookieUtil;

    @BeforeEach
    void setUp() {
        cookieUtil = spy(new CookieUtil(jwtTokenProvider, refreshTokenService));
        reissueService = new ReissueServiceImpl(jwtTokenProvider, refreshTokenService, cookieUtil);
    }

    private final String refreshToken = "refresh-token";
    private final Long userId = 1L;
    private final String username = "user";
    private final String role = "ROLE_USER";
    private final String newAccessToken = "new-access-token";
    private final String newRefreshToken = "new-refresh-token";

    @Test
    void reissueToken_AccessToken_재발급_정상_테스트() {
        // given
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
        given(jwtTokenProvider.getUsername(refreshToken)).willReturn(username);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of(role));
        given(jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_ACCESS, userId, username, role)).willReturn(newAccessToken);
        doNothing().when(refreshTokenService).validateRefreshToken(refreshToken, true);

        // when
        String token = reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, true);

        // then
        assertEquals(newAccessToken, token);
    }

    @Test
    public void reissueToken_RefreshToken_재발급_정상_테스트() {
        // given
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
        given(jwtTokenProvider.getUsername(refreshToken)).willReturn(username);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of(role));
        given(jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_REFRESH, userId, username, role)).willReturn(newRefreshToken);
        doNothing().when(refreshTokenService).validateRefreshToken(refreshToken, true);

        // when
        String token = reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_REFRESH, true);

        // then
        assertEquals(newRefreshToken, token);
    }

    @Test
    void reissueToken_Role추출_예외_테스트() {
        // given
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of());

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, true);
        });

        assertEquals("역할(Role) 정보가 없습니다.", ex.getMessage());
    }

    @Test
    void handleReissueToken_정상_테스트() {
        // given
        given(jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request)).willReturn(refreshToken);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(userId);
        given(jwtTokenProvider.getUsername(refreshToken)).willReturn(username);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of(role));
        given(jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_ACCESS, userId, username, role))
                .willReturn(newAccessToken);
        given(jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_REFRESH, userId, username, role))
                .willReturn(newRefreshToken);

        doNothing().when(refreshTokenService).validateRefreshToken(refreshToken, true);

        // when
        reissueService.handleReissueToken(request, response);

        // then
        verify(response, times(2)).addHeader(eq("Set-Cookie"), anyString());
        verify(refreshTokenService).deleteRefreshToken(userId);
        verify(refreshTokenService).saveRefreshToken(userId, newRefreshToken);
    }

    @Test
    void handleReissueToken_RefreshToken이_없을때_예외_테스트() {
        // given
        given(jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request)).willReturn("");

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.handleReissueToken(request, response);
        });
        assertEquals("Refresh Token이 존재하지 않습니다.", ex.getMessage());
    }

    @Test
    void handleReissueToken_Token_재발급_실패_예외_테스트() {
        // given
        given(jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request)).willReturn(refreshToken);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(userId);
        given(jwtTokenProvider.getUsername(refreshToken)).willReturn(username);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of(role));
        doNothing().when(refreshTokenService).validateRefreshToken(refreshToken, true);

        // Access Token만 null 반환하도록 설정
        doThrow(new IllegalArgumentException("Access Token 재발급이 실패했습니다."))
                .when(cookieUtil).generateAndSetJwtCookies(eq(userId), eq(username), eq(role), eq(response));

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.handleReissueToken(request, response);
        });

        assertEquals("Access Token 재발급이 실패했습니다.", ex.getMessage());
    }
}