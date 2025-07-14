package com.team03.ticketmon.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterUserEntityDTO(
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,
        @NotBlank(message = "사용자 아이디는 필수입니다.")
        String username,
        String password,
        @NotBlank(message = "사용자 이름은 필수입니다.")
        String name,
        @NotBlank(message = "사용자 닉네임은 필수입니다.")
        String nickname,
        @Pattern(
                regexp = "^01[016789]-\\d{3,4}-\\d{4}$",
                message = "올바른 전화번호 형식이 아닙니다. 예: 010-1234-5678")
        // 휴대폰 번호 형식: 010 또는 011/016~019로 시작하며, 가운데는 3~4자리, 마지막은 4자리 숫자
        String phone,
        @NotBlank(message = "사용자 주소는 필수입니다.")
        String address,
        Boolean isSocialUser // 소셜 유저 여부
) {
}
