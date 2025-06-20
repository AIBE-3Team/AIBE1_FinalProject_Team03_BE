package com.team03.ticketmon.user.service;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.domain.entity.SocialUser;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.repository.SocialUserRepository;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialUserServiceImpl implements SocialUserService {

    private final SocialUserRepository socialUserRepository;
    private final UserRepository userRepository;

    @Override
    public void saveSocialUser(OAuthAttributes attributes) {
        UserEntity user = userRepository.findByEmail(attributes.getEmail())
                .orElseThrow(() -> new RuntimeException("유저 정보가 없습니다."));;

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
