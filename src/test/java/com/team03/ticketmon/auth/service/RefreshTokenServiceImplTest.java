package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.domain.entity.RefreshToken;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.auth.repository.RefreshTokenRepository;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("리프레시 토큰 관리 테스트")
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Test
    void saveRefreshToken_정상작동_테스트() {
        // given
        Long userId = 1L;
        String token = "testToken";
        UserEntity userEntity = UserEntity.builder().id(userId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_REFRESH)).thenReturn(10000L);

        // when
        refreshTokenService.saveRefreshToken(userId, token);

        // then
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void saveRefreshToken_유저없으면_예외_테스트() {
        // given
        Long userId = 1L;
        String token = "testToken";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(RuntimeException.class, () -> {
            refreshTokenService.saveRefreshToken(userId, token);
        });
    }

    @Test
    void deleteRefreshToken_정상작동_테스트() {
        // given
        Long userId = 1L;

        // when
        refreshTokenService.deleteRefreshToken(userId);

        // then
        verify(refreshTokenRepository, times(1)).deleteByUserEntityId(userId);
    }
}