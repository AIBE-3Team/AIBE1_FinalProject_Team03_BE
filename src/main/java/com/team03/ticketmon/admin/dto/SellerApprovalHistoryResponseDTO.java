package com.team03.ticketmon.admin.dto;

import com.team03.ticketmon.seller_application.domain.SellerApprovalHistory;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관리자용 판매자 권한 이력 조회 응답 DTO
 * API-04-04: GET /admin/sellers/{userID}/approval-history
 * API-04-06: GET /admin/approvals/history?type=seller
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerApprovalHistoryResponseDTO {

    private Long id;                        // 이력 고유 ID
    private Long userId;                    // 해당 유저 ID
    private String username;                // 해당 유저 아이디 (UserEntity에서 가져옴)
    private String userNickname;            // 해당 유저 닉네임 (UserEntity에서 가져옴)
    private Long sellerApplicationId;       // 해당 신청서 ID
    private SellerApprovalHistory.ActionType type; // 이력 종류 (REQUEST, APPROVED, REJECTED 등)
    private String reason;                  // 반려/해제 사유 (있을 경우)
    private LocalDateTime createdAt;        // 이력 발생 시점

    // 엔티티로부터 DTO를 생성하는 팩토리 메서드 (선택적)
    public static SellerApprovalHistoryResponseDTO fromEntity(SellerApprovalHistory history) {
        // UserEntity와 SellerApplication이 ManyToOne 관계로 매핑되어 있으므로, 직접 접근 가능
        // 단, 지연 로딩(LAZY)이므로 N+1 문제에 주의하며, 필요한 경우 fetch join으로 함께 조회해야함
        // 여기서는 user와 sellerApplication이 null이 아님을 가정하거나 안전하게 처리

        Long userId = (history.getUser() != null) ? history.getUser().getId() : null;
        String username = (history.getUser() != null) ? history.getUser().getUsername() : null;
        String userNickname = (history.getUser() != null) ? history.getUser().getNickname() : null;
        Long sellerApplicationId = (history.getSellerApplication() != null) ? history.getSellerApplication().getId() : null;

        return SellerApprovalHistoryResponseDTO.builder()
                .id(history.getId())
                .userId(userId)
                .username(username)
                .userNickname(userNickname)
                .sellerApplicationId(sellerApplicationId)
                .type(history.getType())
                .reason(history.getReason())
                .createdAt(history.getCreatedAt())
                .build();
    }
}