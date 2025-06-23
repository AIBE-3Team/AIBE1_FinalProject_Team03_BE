package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.UserProfileDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MyPageServiceImpl implements MyPageService {

    private final UserEntityService userEntityService;

    @Override
    public UserProfileDTO getUserProfile(Long userID) {
        UserEntity user = userEntityService.findUserEntityByUserId(userID).orElseThrow(() -> new EntityNotFoundException("회원 정보가 없습니다."));

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
}
