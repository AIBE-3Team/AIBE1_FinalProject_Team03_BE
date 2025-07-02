package com.team03.ticketmon.admin.dto;

import com.team03.ticketmon.seller_application.domain.SellerApplication;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관리자용 판매자 신청 목록 조회 응답 DTO
 * API-04-01: GET /admin/seller-requests 에 대한 응답
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSellerApplicationListResponseDTO {

    private Long applicationId;             // 신청서 고유 ID (PK)
    private Long userId;                    // 신청 유저 ID
    private String username;                // 신청 유저 아이디 (UserEntity에서 가져옴)
    private String userNickname;            // 신청 유저 닉네임 (UserEntity에서 가져옴)
    private String companyName;             // 업체명
    private String businessNumber;          // 사업자등록번호
    private String representativeName;      // 담당자 이름
    private String representativePhone;     // 담당자 연락처
    private String uploadedFileUrl;         // 제출 서류 파일 URL
    private SellerApplication.SellerApplicationStatus status; // 신청 상태 (SUBMITTED, ACCEPTED 등)
    private LocalDateTime createdAt;        // 신청 일시
    private LocalDateTime updatedAt;        // 마지막 처리 일시

    // 엔티티로부터 DTO를 생성하는 팩토리 메서드 (선택적)
    public static AdminSellerApplicationListResponseDTO fromEntity(SellerApplication application) {
        UserEntity user = application.getUser(); // 연관 관계 매핑을 통해 UserEntity 객체에 접근

        return AdminSellerApplicationListResponseDTO.builder()
                .applicationId(application.getId())
                .userId(user != null ? user.getId() : null)
                .username(user != null ? user.getUsername() : null)
                .userNickname(user != null ? user.getNickname() : null)
                .companyName(application.getCompanyName())
                .businessNumber(application.getBusinessNumber())
                .representativeName(application.getRepresentativeName())
                .representativePhone(application.getRepresentativePhone())
                .uploadedFileUrl(application.getUploadedFileUrl())
                .status(application.getStatus())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }
}