package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.service.SocialUserService;
import com.team03.ticketmon.user.service.UserEntityService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final SocialUserService socialUserService;
    private final UserEntityService userEntityService;

    @SuppressWarnings("unchecked")
    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = delegate.loadUser(request);
        String regId = request.getClientRegistration().getRegistrationId();
        OAuthAttributes attr = OAuthAttributes.of(regId, oAuth2User.getAttributes());

        CheckUserEmail(attr);

        if (!socialUserService.existSocialUser(attr.getProvider(), attr.getProviderId()))
            socialUserService.saveSocialUser(attr);

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("USER")),
                attr.getAttributes(),
                attr.getNameAttributeKey()
        );
    }

    private void CheckUserEmail(OAuthAttributes attr) {
        if (!userEntityService.existsByEmail(attr.getEmail())) {
            // UserEntity에 email 정보가 없으면 세션에 OAuthAttributes 저장 후 회원 가입 유도
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                HttpSession session = ((ServletRequestAttributes) requestAttributes).getRequest().getSession();
                session.setAttribute("oauthAttributes", attr);
            }

            OAuth2Error oauth2Error = new OAuth2Error("need_signup", "유저 정보가 없어 회원가입이 필요합니다.", null);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
    }
}
