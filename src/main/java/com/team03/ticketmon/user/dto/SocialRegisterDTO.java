package com.team03.ticketmon.user.dto;

public record SocialRegisterDTO(
        boolean isSocial,
        String name,
        String email
) {
}
