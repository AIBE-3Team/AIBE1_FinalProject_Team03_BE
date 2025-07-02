package com.team03.ticketmon.user.service;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.domain.entity.SocialUser;
import com.team03.ticketmon.user.domain.entity.UserEntity;

import java.util.Optional;

public interface SocialUserService {
    void saveSocialUser(UserEntity user, OAuthAttributes attributes);

    Optional<SocialUser> findByProviderAndProviderId(String provider, String providerId);
}
