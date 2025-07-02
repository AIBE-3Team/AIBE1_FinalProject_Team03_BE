package com.team03.ticketmon.user.service;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.domain.entity.SocialUser;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.repository.SocialUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class SocialUserServiceImpl implements SocialUserService {

    private final SocialUserRepository socialUserRepository;

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
    public Optional<SocialUser> findByProviderAndProviderId(String provider, String providerId) {
        return socialUserRepository.findByProviderAndProviderId(provider, providerId);
    }
}
