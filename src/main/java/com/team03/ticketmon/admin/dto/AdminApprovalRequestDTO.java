package com.team03.ticketmon.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.team03.ticketmon._global.validation.OnReject;

/**
 * 관리자용 판매자 신청 승인/반려 요청 DTO
 * API-04-02: PATCH /admin/seller-requests/{userID} 에 대한 요청
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminApprovalRequestDTO {

    // 승인/반려할 대상 User의 ID는 URL PathVariable로 받으므로 DTO에 포함하지 않습니다.

    @NotNull(message = "승인 여부는 필수입니다.")
    private Boolean approve; // true: 승인, false: 반려

    @NotBlank(message = "반려 사유는 필수입니다.", groups = OnReject.class)
    private String reason; // 반려(approve=false) 시에만 유효한 사유

}