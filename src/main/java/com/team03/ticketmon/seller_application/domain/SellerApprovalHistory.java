package com.team03.ticketmon.seller_application.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import com.team03.ticketmon.user.domain.entity.UserEntity; // UserEntity 임포트

// @Setter - 이력 데이터의 불변성(Immutability), 데이터의 신뢰성과 무결성을 위해 사용하지 않음.
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 사용을 위한 기본 생성자
@AllArgsConstructor(access = AccessLevel.PRIVATE) // @Builder 사용을 위한 모든 필드 생성자
@Entity
@Table(name = "seller_approval_history")
@EntityListeners(AuditingEntityListener.class) // Auditing 기능 활성화를 위해 추가
public class SellerApprovalHistory {    // BaseTimeEntity 상속 제거

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 이력 고유 ID (PK)

    // UserEntity와의 ManyToOne 관계 설정
    // 기존 Long userId 필드를 제거하고 UserEntity 객체 참조로 대체
    @ManyToOne(fetch = FetchType.LAZY) // 지연 로딩 (필요할 때만 로드)
    @JoinColumn(name = "user_id", nullable = false) // 실제 DB 컬럼명 user_id를 외래 키로 사용
    private UserEntity user; // 해당 유저 (UserEntity 객체 자체를 참조)

    // SellerApplication과의 ManyToOne 관계 설정
    // 기존 Long sellerApplicationId 필드를 제거하고 SellerApplication 객체 참조로 대체
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_application_id", nullable = false) // 실제 DB 컬럼명 seller_application_id를 외래 키로 사용
    private SellerApplication sellerApplication; // 해당 신청서 (SellerApplication 객체 자체를 참조)

    @Enumerated(EnumType.STRING) // Enum 타입을 문자열로 저장
    @Column(nullable = false, length = 20) // 이력 종류 (REQUEST, APPROVED 등)
    private ActionType type;

    @Column(length = 100) // 반려/해제 사유 (Nullable)
    private String reason;

    @CreatedDate // 엔티티 생성 시 자동으로 현재 시간 기록
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 생성일시 직접 정의

    // 이력 종류 Enum 정의
    public enum ActionType {
        REQUEST,     // 판매자 권한 요청
        APPROVED,    // 관리자 승인
        REJECTED,    // 관리자 반려
        WITHDRAWN,   // 판매자 자발적 철회
        REVOKED      // 관리자 강제 회수
    }
}