package com.team03.ticketmon.user.dto;

public record UserProfileDTO(
        String email,
        String username,
        String name,
        String nickname,
        String phone,
        String address,
        String profileImage
) {
}