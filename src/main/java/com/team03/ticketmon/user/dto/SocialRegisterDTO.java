package com.team03.ticketmon.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SocialRegisterDTO(
        boolean isSocial,
        String name,
        String email
) {
}
