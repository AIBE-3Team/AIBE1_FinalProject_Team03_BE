package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.user.domain.entity.UserEntity;

public interface RefreshTokenService {
    void deleteRefreshToken(Long userId);
    void saveRefreshToken(Long userId, String token);
}
