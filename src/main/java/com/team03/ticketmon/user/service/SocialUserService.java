package com.team03.ticketmon.user.service;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;

public interface SocialUserService {
    void saveSocialUser(OAuthAttributes attributes);
    boolean existSocialUser(String provider, String providerId);
}
