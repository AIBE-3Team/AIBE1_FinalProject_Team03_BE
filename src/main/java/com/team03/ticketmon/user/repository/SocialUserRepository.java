package com.team03.ticketmon.user.repository;

import com.team03.ticketmon.user.domain.entity.SocialUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialUserRepository extends JpaRepository<SocialUser, Long> {
    boolean existsByProviderAndProviderId(String provider, String providerId);
}
