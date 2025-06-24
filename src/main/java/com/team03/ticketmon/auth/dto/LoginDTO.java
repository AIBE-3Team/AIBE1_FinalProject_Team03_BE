package com.team03.ticketmon.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 요청 DTO")
public record LoginDTO(
        @Schema(description = "사용자 아이디", example = "qwe")
        String username,
        @Schema(description = "사용자 비밀번호 (영어 소문자, 숫자, 특수문자 포함 8자 이상)", example = "1q2w3e4r!")
        String password
) {
}
