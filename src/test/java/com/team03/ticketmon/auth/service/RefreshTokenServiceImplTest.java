package com.team03.ticketmon.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.auth.domain.entity.RefreshToken;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("리프레시 토큰 관리 테스트")
class RefreshTokenServiceImplTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private ObjectMapper objectMapper;
    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private final String refreshToken = "refresh-token";
    private final Long userId = 1L;
    private final Long refreshExpirationMs = 86400000L;
    private final String redisKey = RedisKeyGenerator.JWT_RT_PREFIX + userId;
    private RefreshToken storedToken;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(
                jwtTokenProvider,
                userRepository,
                redisTemplate,
                objectMapper
        );

        storedToken = RefreshToken.builder().id(userId).token(refreshToken).created_at(LocalDateTime.now()).build();

        // @Value 대체 수동 설정
        try {
            java.lang.reflect.Field field = RefreshTokenServiceImpl.class.getDeclaredField("refreshExpirationMs");
            field.setAccessible(true);
            field.set(refreshTokenService, refreshExpirationMs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void saveRefreshToken_정상작동_테스트() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(userRepository.existsById(userId)).willReturn(true);

        // when
        refreshTokenService.saveRefreshToken(userId, refreshToken);

        // then
        verify(userRepository).existsById(userId);
        verify(redisTemplate.opsForValue()).set(
                eq(redisKey),
                any(RefreshToken.class),
                eq(refreshExpirationMs),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void saveRefreshToken_유저없으면_예외_테스트() {
        // given
        given(userRepository.existsById(userId)).willReturn(false);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            refreshTokenService.saveRefreshToken(userId, refreshToken);
        });

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void deleteRefreshToken_정상작동_테스트() {
        // given

        // when
        refreshTokenService.deleteRefreshToken(userId);

        // then
        verify(redisTemplate).delete(redisKey);
    }

    @Test
    public void validateRefreshToken_정상작동_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.isTokenExpired(refreshToken)).willReturn(false);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForValue().get(redisKey)).willReturn(storedToken);
        given(objectMapper.convertValue(storedToken, RefreshToken.class)).willReturn(storedToken);

        // when & then
        assertDoesNotThrow(() -> refreshTokenService.validateRefreshToken(refreshToken, true));
    }

    @Test
    void validateRefreshToken_유효하지_않는_카테고리_예외_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_ACCESS);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            refreshTokenService.validateRefreshToken(refreshToken, true);
        });

        assertEquals(ErrorCode.INVALID_TOKEN, ex.getErrorCode());
    }

    @Test
    void validateRefreshToken_RefreshToken_만료_예외_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.isTokenExpired(refreshToken)).willReturn(true);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            refreshTokenService.validateRefreshToken(refreshToken, true);
        });

        assertEquals(ErrorCode.INVALID_TOKEN, ex.getErrorCode());
    }

    @Test
    public void validateRefreshToken_저장소_존재하지_않음_예외_테스트() {
        // given
        given(jwtTokenProvider.getCategory(refreshToken)).willReturn(jwtTokenProvider.CATEGORY_REFRESH);
        given(jwtTokenProvider.isTokenExpired(refreshToken)).willReturn(false);
        given(jwtTokenProvider.getUserId(refreshToken)).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForValue().get(redisKey)).willReturn(storedToken);
        given(objectMapper.convertValue(storedToken, RefreshToken.class)).willReturn(null);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            refreshTokenService.validateRefreshToken(refreshToken, true);
        });

        assertEquals(ErrorCode.INVALID_TOKEN, ex.getErrorCode());
    }
}