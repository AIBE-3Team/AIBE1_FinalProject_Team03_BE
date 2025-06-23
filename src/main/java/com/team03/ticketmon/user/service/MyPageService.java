package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.dto.UserProfileDTO;

public interface MyPageService {
    UserProfileDTO getUserProfile(Long userID);
}
