package com.team03.ticketmon.user.service;

import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {
    String uploadProfileAndReturnUrl(MultipartFile profileImage);
}
