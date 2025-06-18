package com.team03.ticketmon.queue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 클라이언트가 대기열 진입을 요청할 때 사용하는 DTO
 *
 * @param concertId 진입을 원하는 콘서트의 고유 ID
 * @param userId    요청하는 사용자의 고유 ID (실제 운영에서는 보안을 위해 인증된 사용자 정보로 대체되어야 합니다)
 */
public record EnterRequest(
        @NotNull(message = "콘서트 ID는 필수 입력값입니다.")
        Long concertId,
        @NotBlank(message = "사용자 ID는 필수 입력값이며, 공백일 수 없습니다.")
        String userId
) {
}
