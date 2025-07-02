package com.team03.ticketmon.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 관리자용 판매자 강제 권한 해제 요청 DTO
 * API-04-03: DELETE /admin/sellers/{userID}/role 에 대한 요청
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminRevokeRequestDTO {

    // 권한 해제할 대상 User의 ID는 URL PathVariable로 받으므로 DTO에 포함하지 않음

    @NotBlank(message = "강제 권한 해제 사유는 필수입니다.")
    @Size(max = 255, message = "강제 권한 해제 사유는 255자를 초과할 수 없습니다.")
    private String reason; // 강제 권한 해제 사유 (예: "정책 위반으로 인한 권한 해제")
}