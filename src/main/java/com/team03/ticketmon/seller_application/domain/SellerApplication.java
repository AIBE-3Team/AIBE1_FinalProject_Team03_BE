package com.team03.ticketmon.seller_application.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import java.util.List; // List 임포트
import com.team03.ticketmon.user.domain.entity.UserEntity; // UserEntity 임포트

@Entity
@Table(name = "seller_applications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;    // 신청서 고유 ID (PK)

    // UserEntity와의 ManyToOne 관계 설정
    // 기존 Long userId 필드를 제거하고 UserEntity 객체 참조로 대체
    @ManyToOne(fetch = FetchType.LAZY) // 지연 로딩 (필요할 때만 로드)
    @JoinColumn(name = "user_id", nullable = false) // 실제 DB 컬럼명 user_id를 외래 키로 사용
    private UserEntity user; // 신청 유저 (UserEntity 객체 자체를 참조)

    @Column(nullable = false, length = 100)
    private String companyName; // 업체명 (기회사명/거래처명 등)

    @Column(nullable = false, unique = true, length = 10)
    private String businessNumber; // 사업자등록번호 (하이픈 없이 10자리)

    @Column(nullable = false, length = 50)
    private String representativeName; // 담당자(대표자) 이름

    @Column(nullable = false, length = 20)
    private String representativePhone; // 담당자(대표자) 연락처

    @Column(nullable = false, columnDefinition = "TEXT")
    private String uploadedFileUrl; // 제출 서류 파일 URL (사업자등록증.pdf, jpg 등)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerApplicationStatus status; // 신청 상태 (SUBMITTED, ACCEPTED, REJECTED, REVOKED, WITHDRAWN)

    @Column(nullable = false)
    private LocalDateTime createdAt; // 신청 일시

    private LocalDateTime updatedAt; // 관리자가 신청 처리(승인/반려 등)한 시점 (Nullable)

    // 개인정보 처리 정책 관련 필드 추가
    @Column(nullable = true)
    private LocalDateTime maskedAt; // 개인정보가 마스킹 처리된 시점 (representativeName, representativePhone 등)

    @Column(nullable = true)
    private LocalDateTime deletedAt; // uploaded_file_url이 가리키는 실제 파일이 스토리지에서 삭제된 시점

    // SellerApprovalHistory와의 OneToMany 관계 추가 (양방향 매핑)
    // mappedBy는 연관관계의 주인이 아닌 쪽에 설정. SellerApprovalHistory의 sellerApplication 필드에 의해 매핑됨
    @OneToMany(mappedBy = "sellerApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default // 빌더 사용 시 기본값으로 빈 리스트 초기화
    private List<SellerApprovalHistory> approvalHistories = new java.util.ArrayList<>();

    // 새로운 SellerApplicationStatus Enum 정의
    public enum SellerApplicationStatus {
        SUBMITTED, // 신청서 제출됨 (초기 상태)
        ACCEPTED,  // 관리자에 의해 승인됨
        REJECTED,  // 관리자에 의해 반려됨
        REVOKED,    // 관리자에 의해 강제 권한 회수됨 (정책 위반 등)
        WITHDRAWN  // 판매자가 자발적으로 권한 해제함
    }

    @PrePersist // 엔티티가 영속화되기 전에 실행될 콜백 메서드
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();    // 최초 생성 시점 자동 기록
        }
        if (status == null) {
            status = SellerApplicationStatus.SUBMITTED; // 기본 상태는 SUBMITTED
        }
    }

    @PreUpdate // 엔티티가 업데이트되기 전에 실행될 콜백 메서드
    public void preUpdate() {
        updatedAt = LocalDateTime.now(); // 업데이트 시점 자동 기록
    }
}