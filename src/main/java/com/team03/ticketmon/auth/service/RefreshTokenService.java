package com.team03.ticketmon.auth.service;

public interface RefreshTokenService {
    void deleteRefreshToken(Long userId);
    void saveRefreshToken(Long userId, String token);
    boolean existToken(Long userId, String token);
}
