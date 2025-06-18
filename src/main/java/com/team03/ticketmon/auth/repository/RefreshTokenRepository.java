package com.team03.ticketmon.auth.repository;

import com.team03.ticketmon.auth.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    void deleteByUserEntityId(Long userId);
    Optional<String> findRefreshTokenByUserEntityIdAndToken(Long userId, String token);
}
