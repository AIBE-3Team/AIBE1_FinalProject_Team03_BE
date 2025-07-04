// src/main/java/com/team03/ticketmon/seller_application/dto/ApplicantInformationResponseDTO.java

package com.team03.ticketmon.seller_application.dto; // seller_application.dto 패키지에 생성

import com.team03.ticketmon.user.domain.entity.UserEntity; // UserEntity의 Role, ApprovalStatus 사용
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * GET /api/users/me/applicant-info API 응답 DTO
 * 현재 로그인된 사용자의 신청자 관련 모든 필요한 정보를 프론트엔드에 제공합니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicantInformationResponseDTO {
    private Long id; // 사용자 고유 ID
    private String username; // 사용자 아이디
    private String nickname; // 사용자 닉네임
    private String email;    // 사용자 이메일
    private String name;     // 사용자 실명
    private String phone;    // 사용자 전화번호
    private UserEntity.Role role; // 사용자 역할 (USER, SELLER, ADMIN)
    private UserEntity.ApprovalStatus approvalStatus; // 판매자 승인 상태 (null, PENDING, APPROVED 등)

    // UserEntity로부터 ApplicantInformationResponseDTO를 생성하는 팩토리 메서드
    public static ApplicantInformationResponseDTO fromEntity(UserEntity user) { //
        return ApplicantInformationResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .role(user.getRole())
                .approvalStatus(user.getApprovalStatus())
                .build();
    }
}