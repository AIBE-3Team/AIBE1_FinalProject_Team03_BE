package com.team03.ticketmon.seller_application.repository;

import com.team03.ticketmon.seller_application.domain.SellerApplication;
import com.team03.ticketmon.seller_application.domain.SellerApplication.SellerApplicationStatus; // 새로 정의한 Enum 임포트
import com.team03.ticketmon.user.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    // 특정 userId로 신청 정보를 조회 (유저 당 여러 개의 신청서를 가질 수 있으므로 List 반환)
//    List<SellerApplication> findByUserId(Long userId);
    List<SellerApplication> findByUser(UserEntity user); // UserEntity 객체로 조회하도록 변경

    // 특정 userId의 특정 상태 신청이 존재하는지 확인 (예: SUBMITTED 상태의 신청 여부)
//    boolean existsByUserIdAndStatus(Long userId, SellerApplicationStatus status);
    boolean existsByUserAndStatus(UserEntity user, SellerApplicationStatus status); // UserEntity 객체로 조회하도록 변경

    // 특정 상태의 신청 목록 조회 (관리자 페이지용, 예: SUBMITTED 상태 목록)
    List<SellerApplication> findByStatus(SellerApplicationStatus status);

    // 가장 최신 (현재 진행 중인 또는 마지막으로 처리된) 신청서 조회
    // activeStatuses 예시: List.of(SellerApplicationStatus.SUBMITTED, SellerApplicationStatus.ACCEPTED)
//    Optional<SellerApplication> findTopByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<SellerApplicationStatus> statuses);
    Optional<SellerApplication> findTopByUserAndStatusInOrderByCreatedAtDesc(UserEntity user, List<SellerApplicationStatus> statuses); // UserEntity 객체로 조회하도록 변경

    // 특정 UserEntity에 대한 가장 최신 SellerApplication을 조회 (revokeSellerRole에서 사용)
    Optional<SellerApplication> findTopByUserOrderByCreatedAtDesc(UserEntity user);

    /* 스케줄러를 위한 추가 조회 메서드 예시 (개인정보 보호 정책 관련) */
    // WITHDRAWN 상태이고, 개인정보 마스킹 시점(maskedAt)이 null이며, createdAt이 특정 시간 이전인 목록 조회
    List<SellerApplication> findByStatusAndMaskedAtIsNullAndCreatedAtBefore(SellerApplicationStatus status, LocalDateTime dateTime);

    // REJECTED 상태이고, 실제 파일 삭제 시점(deletedAt)이 null이며, updatedAt이 특정 시간 이전인 목록 조회
    List<SellerApplication> findByStatusAndDeletedAtIsNullAndUpdatedAtBefore(SellerApplicationStatus status, LocalDateTime dateTime);

    // REVOKED 상태이고, 개인정보 마스킹 시점(maskedAt)이 null이며, updatedAt이 특정 시간 이전인 목록 조회
    List<SellerApplication> findByStatusAndMaskedAtIsNullAndUpdatedAtBefore(SellerApplicationStatus status, LocalDateTime dateTime);

}