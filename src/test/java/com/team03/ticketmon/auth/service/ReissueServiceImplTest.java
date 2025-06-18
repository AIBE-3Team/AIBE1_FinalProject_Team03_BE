package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

        // when
        String token = reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS);
        
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

        // when
        String token = reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_REFRESH);

        // then
        assertEquals(newRefreshToken, token);
    }
    
    @Test
    void reissueToken_유효하지_않는_카테고리_예외_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_ACCESS);

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS);
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
            reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS);
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

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS);
        });

        assertEquals("역할(Role) 정보가 없습니다.", ex.getMessage());
    }
    
    @Test
    public void handleReissueTokne_정상_테스트() {
        // given
        given(jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request)).willReturn(refreshToken);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(userId);

        Cookie accessCookie = new Cookie(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken);
        Cookie refreshCookie = new Cookie(jwtTokenProvider.CATEGORY_REFRESH, newRefreshToken);

        given(jwtTokenProvider.createCookie(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken)).willReturn(accessCookie);
        given(jwtTokenProvider.createCookie(jwtTokenProvider.CATEGORY_REFRESH, newRefreshToken)).willReturn(refreshCookie);

        // 필요한 동작만 spy 처리
        ReissueServiceImpl spyService = spy(reissueService);
        doReturn(newAccessToken).when(spyService).reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS);
        doReturn(newRefreshToken).when(spyService).reissueToken(refreshToken, jwtTokenProvider.CATEGORY_REFRESH);

        // when
        spyService.handleReissueToken(request, response);

        // then
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response, times(2)).addCookie(cookieCaptor.capture());

        List<Cookie> capturedCookies = cookieCaptor.getAllValues();

        boolean hasAccess = capturedCookies.stream()
                .anyMatch(c -> c.getName().equals(jwtTokenProvider.CATEGORY_ACCESS) && c.getValue().equals(newAccessToken));
        boolean hasRefresh = capturedCookies.stream()
                .anyMatch(c -> c.getName().equals(jwtTokenProvider.CATEGORY_REFRESH) && c.getValue().equals(newRefreshToken));

        verify(refreshTokenService).deleteRefreshToken(userId);
        verify(refreshTokenService).saveRefreshToken(userId, newRefreshToken);

        assertTrue(hasAccess);
        assertTrue(hasRefresh);
    }
    
    @Test
    public void handleReissueTokne_RefreshToken이_없을때_예외_테스트() {
        // given
        given(jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request)).willReturn("");

        // when & then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.handleReissueToken(request, response);
        });
        assertEquals("Refresh Token이 존재하지 않습니다.", ex.getMessage());
    }
    
    @Test
    public void handleReissueTokne_Token_재발급_실패_예외_테스트() {
        // given
        given(jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request)).willReturn(refreshToken);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(userId);
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.getRoles(refreshToken)).willReturn(List.of(role));

        // when
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            reissueService.handleReissueToken(request, response);
        });
        
        // then
        assertEquals("Token 재발급이 실패했습니다.", ex.getMessage());
    }
}