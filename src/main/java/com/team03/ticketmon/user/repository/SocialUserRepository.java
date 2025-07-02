package com.team03.ticketmon.user.repository;

import com.team03.ticketmon.user.domain.entity.SocialUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialUserRepository extends JpaRepository<SocialUser, Long> {
    @EntityGraph(attributePaths = {"userEntity"})
    Optional<SocialUser> findByProviderAndProviderId(String provider, String providerId);
}
