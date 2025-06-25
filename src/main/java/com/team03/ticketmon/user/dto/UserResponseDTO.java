package com.team03.ticketmon.user.dto;

public record UserResponseDTO(
        Long userId,
        String username,
        String nickname,
        String role
) {
}
