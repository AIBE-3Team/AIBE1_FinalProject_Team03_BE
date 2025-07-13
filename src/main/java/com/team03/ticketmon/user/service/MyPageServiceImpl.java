package com.team03.ticketmon.user.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.service.UrlConversionService;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.UpdatePasswordDTO;
import com.team03.ticketmon.user.dto.UpdateUserProfileDTO;
import com.team03.ticketmon.user.dto.UserProfileDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MyPageServiceImpl implements MyPageService {

    private final UserEntityService userEntityService;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;
    private final UrlConversionService urlConversionService;

    @Override
    public UserProfileDTO getUserProfile(Long userId) {
        UserEntity user = findUserOrThrow(userId);

        String convertedProfileImageUrl = urlConversionService.convertToCloudFrontUrl(user.getProfileImage());

        return new UserProfileDTO(
                user.getEmail(),
                user.getUsername(),
                user.getName(),
                user.getNickname(),
                user.getPhone(),
                user.getAddress(),
                convertedProfileImageUrl
        );
    }

    @Override
    public void updateUserProfile(Long userId, UpdateUserProfileDTO dto) {
        log.info("회원 정보 변경 요청 : userId={}", userId);
        UserEntity user = findUserOrThrow(userId);

        if (dto.profileImage() != null && !dto.profileImage().isEmpty()) {
            // 기존 프로필 이미지가 있으면 저장소에서 삭제
            if (user.getProfileImage() != null && !user.getProfileImage().isEmpty())
                userProfileService.deleteProfileImage(user.getProfileImage());

            userProfileService.uploadProfileAndReturnUrl(dto.profileImage()).ifPresent(user::setProfileImage);
        }

        user.setName(dto.name());
        user.setNickname(dto.nickname());
        user.setPhone(dto.phone());
        user.setAddress(dto.address());

        userEntityService.save(user);
        log.info("회원 정보 변경 성공 : userId={}", userId);
    }

    @Override
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        log.info("비밀번호 변경 요청 : userId={}", userId);
        UserEntity user = findUserOrThrow(userId);

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            log.warn("비밀번호 불일치 : userId={}", userId);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호가 일치하지 않습니다.");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));

        userEntityService.save(user);
        log.info("비밀번호 변경 성공 : userId={}", userId);
    }

    private UserEntity findUserOrThrow(Long userId) {
        return userEntityService.findUserEntityByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
