package com.team03.ticketmon.seller_application.repository;

import com.team03.ticketmon.seller_application.domain.SellerApprovalHistory;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// SellerApprovalHistory 엔티티와 해당 엔티티의 PK 타입(Long)을 명시
@Repository
public interface SellerApprovalHistoryRepository extends JpaRepository<SellerApprovalHistory, Long> {

    // 특정 UserEntity에 대한 모든 SellerApprovalHistory 기록을 최신순으로 조회
    // 관리자 페이지의 '판매자 권한 상세 보기 (특정 유저 이력 조회)' 기능 (API-04-04)에 사용
    // OrderByCreatedAtDesc를 통해 생성일시 기준 내림차순으로 정렬
    List<SellerApprovalHistory> findByUserOrderByCreatedAtDesc(UserEntity user);

    // 특정 UserEntity와 특정 ActionType 목록에 해당하는 가장 최신 SellerApprovalHistory를 조회
    // 주로 UserEntity의 lastReason 필드를 채우기 위해 (반려/회수 사유) 사용
    Optional<SellerApprovalHistory> findTopByUserAndTypeInOrderByCreatedAtDesc(
            UserEntity user,
            List<SellerApprovalHistory.ActionType> types);

    // 특정 ActionType으로 필터링된 모든 SellerApprovalHistory 기록을 최신순으로 페이징 조회
    // 관리자 페이지의 '전체 판매자 이력 목록 조회' 기능 (API-04-06)에 사용
    Page<SellerApprovalHistory> findByTypeOrderByCreatedAtDesc(SellerApprovalHistory.ActionType actionType, Pageable pageable);

    // 키워드 검색 및 타입 필터링을 지원하는 전체 SellerApprovalHistory 조회 (페이징 포함)
    // keyword는 user (username, nickname), sellerApplication (companyName, businessNumber, representativeName),
    // 그리고 history (reason, type) 필드에서 검색
    // JPQL에서 CONCAT을 사용하여 LIKE 검색을 구현
    @Query("SELECT sah FROM SellerApprovalHistory sah " +
            "LEFT JOIN sah.user u " + // UserEntity와 조인
            "LEFT JOIN sah.sellerApplication sa " + // SellerApplication과 조인
            "WHERE (:typeFilter IS NULL OR sah.type = :typeFilter) " + // 타입 필터 적용
            "AND (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(sa.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(sa.businessNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(sa.representativeName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(sah.reason) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(CAST(sah.type AS string)) LIKE LOWER(CONCAT('%', :keyword, '%'))) " + // Enum도 문자열로 변환하여 검색
            "ORDER BY sah.createdAt DESC") // 최신순 정렬
    Page<SellerApprovalHistory> findAllWithFilters(
            @Param("typeFilter") SellerApprovalHistory.ActionType typeFilter,
            @Param("keyword") String keyword,
            Pageable pageable);

    // 키워드만 있는 경우 (typeFilter가 없는 경우)를 위한 오버로드 메서드
    @Query("SELECT sah FROM SellerApprovalHistory sah " +
            "LEFT JOIN sah.user u " +
            "LEFT JOIN sah.sellerApplication sa " +
            "WHERE (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(sa.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(sa.businessNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(sa.representativeName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(sah.reason) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(CAST(sah.type AS string)) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY sah.createdAt DESC")
    Page<SellerApprovalHistory> findAllByKeywordOrderByCreatedAtDesc(
            @Param("keyword") String keyword,
            Pageable pageable);
}