package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.UpdatePasswordDTO;
import com.team03.ticketmon.user.dto.UpdateUserProfileDTO;
import com.team03.ticketmon.user.dto.UserProfileDTO;
import jakarta.persistence.EntityNotFoundException;
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

    @Override
    public UserProfileDTO getUserProfile(Long userId) {
        UserEntity user = userEntityService.findUserEntityByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("회원 정보가 없습니다."));

        return new UserProfileDTO(
                user.getEmail(),
                user.getUsername(),
                user.getName(),
                user.getNickname(),
                user.getPhone(),
                user.getAddress(),
                user.getProfileImage()
        );
    }

    @Override
    public void updateUserProfile(Long userId, UpdateUserProfileDTO dto) {
        log.info("회원 정보 변경 요청 : userId={}", userId);
        UserEntity user = userEntityService.findUserEntityByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("회원 정보가 없습니다."));

        user.setNickname(dto.nickname());
        user.setPhone(dto.phone());
        user.setAddress(dto.address());
        user.setProfileImage(dto.profileImage());

        userEntityService.save(user);
        log.info("회원 정보 변경 성공 : userId={}", userId);
    }

    @Override
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        log.info("비밀번호 변경 요청 : userId={}", userId);
        UserEntity user = userEntityService.findUserEntityByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("회원 정보가 없습니다."));

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            log.error("비밀번호 불일치 : userId={}", userId);
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));

        userEntityService.save(user);
        log.info("비밀번호 변경 성공 : userId={}", userId);
    }
}
