package com.team03.ticketmon.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserProfileDTO(
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,
        @NotBlank(message = "사용자 아이디는 필수입니다.")
        String username,
        @NotBlank(message = "사용자명은 필수입니다.")
        String name,
        @NotBlank(message = "사용자 닉네임은 필수입니다.")
        String nickname,
        @Pattern(regexp = "^[0-9-]+$", message = "올바른 전화번호 형식이 아닙니다")
        String phone,
        @NotBlank(message = "사용자 주소는 필수입니다.")
        String address,
        String profileImage
) {
}
