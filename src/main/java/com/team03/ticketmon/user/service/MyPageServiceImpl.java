package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.UpdateUserProfileDTO;
import com.team03.ticketmon.user.dto.UserProfileDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MyPageServiceImpl implements MyPageService {

    private final UserEntityService userEntityService;

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
        UserEntity user = userEntityService.findUserEntityByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("회원 정보가 없습니다."));

        user.setEmail(dto.email());
        user.setUsername(dto.username());
        user.setName(dto.name());
        user.setNickname(dto.nickname());
        user.setPhone(dto.phone());
        user.setAddress(dto.address());
        user.setProfileImage(dto.profileImage());

        userEntityService.save(user);
    }
}
