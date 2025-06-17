package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.domain.entity.RefreshToken;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.auth.repository.RefreshTokenRepository;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    public void saveRefreshToken(Long userId, String token) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("유저 정보가 없습니다."));

        RefreshToken refreshToken = RefreshToken.builder()
                .userEntity(user)
                .token(token)
                .expiration(getExpirationTime())
                .build();

        refreshTokenRepository.save(refreshToken);
    }

    @Override
    public void deleteRefreshToken(Long userId) {
        refreshTokenRepository.deleteByUserEntityId(userId);
    }

    private LocalDateTime getExpirationTime() {
        Instant now = Instant.now();
        Instant expirationInstant = now.plusMillis(jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_REFRESH));
        return LocalDateTime.ofInstant(expirationInstant, ZoneId.of("UTC"));
    }
}
