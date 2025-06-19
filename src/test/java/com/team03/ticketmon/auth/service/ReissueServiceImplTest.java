package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
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
    private CookieUtil cookieUtil;
    @Mock
    HttpServletRequest request;
    @Mock
    HttpServletResponse response;
    @InjectMocks
    private ReissueServiceImpl reissueService;

    private final String refreshToken = "refresh-token";
    private final Long userId = 1L;
    private final String role = "ROLE_USER";
    private final String newAccessToken = "new-access-token";
    private final String newRefreshToken = "new-refresh-token";

    @Test
    void reissueToken_AccessToken_재발급_정상_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.isTokenExpired(refreshToken)).willReturn(false);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of(role));
        given(jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_ACCESS, userId, role)).willReturn(newAccessToken);
        given(refreshTokenService.existToken(userId, refreshToken)).willReturn(true);

        // when
        String token = reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, true);
        
        // then
        assertEquals(newAccessToken, token);
    }

    @Test
    public void reissueToken_RefreshToken_재발급_정상_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.isTokenExpired(refreshToken)).willReturn(false);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of(role));
        given(jwtTokenProvider.generateToken(jwtTokenProvider.CATEGORY_REFRESH, userId, role)).willReturn(newRefreshToken);
        given(refreshTokenService.existToken(userId, refreshToken)).willReturn(true);

        // when
        String token = reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_REFRESH, true);

        // then
        assertEquals(newRefreshToken, token);
    }
    
    @Test
    void reissueToken_유효하지_않는_카테고리_예외_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_ACCESS);

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, true);
        });

        assertEquals("유효하지 않은 카테고리 JWT 토큰입니다.", ex.getMessage());
    }
    
    @Test
    void reissueToken_RefreshToken_만료_예외_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.isTokenExpired(refreshToken)).willReturn(true);

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, true);
        });

        assertEquals("Refresh Token이 만료되었습니다.", ex.getMessage());
    }

    @Test
    void reissueToken_Role추출_예외_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.isTokenExpired(refreshToken)).willReturn(false);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of());
        given(refreshTokenService.existToken(userId, refreshToken)).willReturn(true);

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, true);
        });

        assertEquals("역할(Role) 정보가 없습니다.", ex.getMessage());
    }
    
    @Test
    public void reissueToken_DB_존재하지_않음_예외_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.isTokenExpired(refreshToken)).willReturn(false);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
        given(refreshTokenService.existToken(userId, refreshToken)).willReturn(false);

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, true);
        });

        assertEquals("Refresh Token이 DB에 존재하지 않습니다.", ex.getMessage());
    }
    
    @Test
    void handleReissueToken_정상_테스트() {
        // given
        given(jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request)).willReturn(refreshToken);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(userId);
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.isTokenExpired(refreshToken)).willReturn(false);
        given(refreshTokenService.existToken(userId, refreshToken)).willReturn(true);

        ResponseCookie accessCookie = ResponseCookie.from(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken)
                .httpOnly(true).secure(true).path("/").maxAge(Duration.ofMinutes(10)).build();

        ResponseCookie refreshCookie = ResponseCookie.from(jwtTokenProvider.CATEGORY_REFRESH, newRefreshToken)
                .httpOnly(true).secure(true).path("/").maxAge(Duration.ofDays(1)).build();

        Long accessCookieExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_ACCESS);
        Long refreshCookieExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_REFRESH);

        given(cookieUtil.createCookie(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken, accessCookieExp)).willReturn(accessCookie);
        given(cookieUtil.createCookie(jwtTokenProvider.CATEGORY_REFRESH, newRefreshToken, refreshCookieExp)).willReturn(refreshCookie);

        // 필요한 동작만 spy 처리
        ReissueServiceImpl spyService = spy(reissueService);
        doReturn(newAccessToken).when(spyService).reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, false);
        doReturn(newRefreshToken).when(spyService).reissueToken(refreshToken, jwtTokenProvider.CATEGORY_REFRESH, false);

        // when
        spyService.handleReissueToken(request, response);

        // then
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);

        verify(response, times(2)).addHeader(eq("Set-Cookie"), headerCaptor.capture());
        List<String> setCookieHeaders = headerCaptor.getAllValues();

        boolean hasAccess = setCookieHeaders.stream().anyMatch(h -> h.contains(newAccessToken));
        boolean hasRefresh = setCookieHeaders.stream().anyMatch(h -> h.contains(newRefreshToken));

        verify(refreshTokenService).deleteRefreshToken(userId);
        verify(refreshTokenService).saveRefreshToken(userId, newRefreshToken);

        assertTrue(hasAccess);
        assertTrue(hasRefresh);
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
        given(refreshTokenService.existToken(userId, refreshToken)).willReturn(true);
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of(role));

        ReissueServiceImpl spyService = spy(reissueService);
        doReturn(null).when(spyService).reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, false);

        // when
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            spyService.handleReissueToken(request, response);
        });
        
        // then
        assertEquals("Access Token 재발급이 실패했습니다.", ex.getMessage());
    }
}