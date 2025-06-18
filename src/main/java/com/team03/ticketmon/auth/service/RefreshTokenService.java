package com.team03.ticketmon.auth.service;

import java.util.Optional;

public interface RefreshTokenService {
    void deleteRefreshToken(Long userId);
    void saveRefreshToken(Long userId, String token);
    Optional<String> findRefreshToken(Long userId, String token);
}
