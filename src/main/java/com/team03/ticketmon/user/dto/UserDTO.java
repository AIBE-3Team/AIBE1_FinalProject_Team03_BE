package com.team03.ticketmon.user.dto;

public record UserDTO(
        String email,
        String username,
        String password,
        String name,
        String nickname,
        String phone,
        String address,
        String profileImage
) {
}
