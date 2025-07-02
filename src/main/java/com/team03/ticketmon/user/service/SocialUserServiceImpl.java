package com.team03.ticketmon.user.service;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.domain.entity.SocialUser;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.repository.SocialUserRepository;
import com.team03.ticketmon.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SocialUserServiceImpl implements SocialUserService {

    private final SocialUserRepository socialUserRepository;
    private final UserRepository userRepository;

    @Override
    public void saveSocialUser(UserEntity user, OAuthAttributes attributes) {
        SocialUser socialUser = SocialUser.builder()
                .provider(attributes.getProvider())
                .providerId(attributes.getProviderId())
                .userEntity(user)
                .build();

        socialUserRepository.save(socialUser);
    }

    @Override
    public boolean existSocialUser(String provider, String providerId) {
        return socialUserRepository.existsByProviderAndProviderId(provider, providerId);
    }
}
