package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.dto.UpdateUserProfileDTO;
import com.team03.ticketmon.user.dto.UserProfileDTO;

public interface MyPageService {
    UserProfileDTO getUserProfile(Long userId);
    void updateUserProfile(Long userId, UpdateUserProfileDTO dto);
}
