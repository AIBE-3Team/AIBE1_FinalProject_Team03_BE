package com.team03.ticketmon.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePasswordDTO(
        @NotBlank
        String currentPassword,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=-])[A-Za-z\\d!@#$%^&*()_+=-]{8,}$",
                message = "비밀번호는 최소 8자 이상이며, 소문자, 숫자, 특수문자를 포함해야 합니다."
        )
        String newPassword
) {
}
