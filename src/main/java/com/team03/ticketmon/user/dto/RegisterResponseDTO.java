package com.team03.ticketmon.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 가입 응답 DTO")
public record RegisterResponseDTO(
        @Schema(description = "회원 가입 성공 여부")
        boolean isSuccess,
        @Schema(description = "중복 발생 항목 ex)email, username, name")
        String type,
        @Schema(description = "중복 발생 메시지")
        String message
) {
}
