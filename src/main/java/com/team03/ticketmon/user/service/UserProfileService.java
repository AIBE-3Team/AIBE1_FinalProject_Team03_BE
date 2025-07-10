package com.team03.ticketmon.user.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface UserProfileService {
    Optional<String> uploadProfileAndReturnUrl(MultipartFile profileImage);
    void deleteProfileImage(String profileImageUrl);
}
