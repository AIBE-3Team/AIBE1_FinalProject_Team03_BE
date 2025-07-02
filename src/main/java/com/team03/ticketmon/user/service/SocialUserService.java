package com.team03.ticketmon.user.service;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.domain.entity.UserEntity;

public interface SocialUserService {
    void saveSocialUser(UserEntity user, OAuthAttributes attributes);
    boolean existSocialUser(String provider, String providerId);
}
