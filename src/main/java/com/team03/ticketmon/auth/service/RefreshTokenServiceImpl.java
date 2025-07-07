package com.team03.ticketmon.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.auth.domain.entity.RefreshToken;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void saveRefreshToken(Long userId, String token) {
        if (!userRepository.existsById(userId))
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);

        RefreshToken refreshToken = RefreshToken.builder()
                .id(userId)
                .token(token)
                .created_at(LocalDateTime.now())
                .build();

        String redisKey = RedisKeyGenerator.JWT_RT_PREFIX + userId;
        redisTemplate.opsForValue().set(redisKey, refreshToken, refreshExpirationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(RedisKeyGenerator.JWT_RT_PREFIX + userId);
    }

    @Override
    public void validateRefreshToken(String refreshToken, boolean dbCheck) {
        String category = jwtTokenProvider.getCategory(refreshToken);
        if (!jwtTokenProvider.CATEGORY_REFRESH.equals(category))
            throw new IllegalArgumentException("유효하지 않은 카테고리 JWT 토큰입니다.");

        if (jwtTokenProvider.isTokenExpired(refreshToken))
            throw new IllegalArgumentException("Refresh Token이 만료되었습니다.");

        if (dbCheck) {
            Long userId = jwtTokenProvider.getUserId(refreshToken);
            RefreshToken storedToken = getRefreshToken(userId);
            if (storedToken == null || !storedToken.getToken().equals(refreshToken))
                throw new IllegalArgumentException("Refresh Token이 존재하지 않습니다.");
        }
    }

    @Override
    public RefreshToken getRefreshToken(Long userId) {
        String key = RedisKeyGenerator.JWT_RT_PREFIX + userId;
        Object value = redisTemplate.opsForValue().get(key);
        return objectMapper.convertValue(value, RefreshToken.class);
    }
}
