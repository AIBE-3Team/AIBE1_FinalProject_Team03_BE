package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.domain.entity.RefreshToken;

public interface RefreshTokenService {
    void deleteRefreshToken(Long userId);
    void saveRefreshToken(Long userId, String token);
    void validateRefreshToken(String refreshToken, boolean dbCheck);
    RefreshToken getRefreshToken(Long userId);
}
