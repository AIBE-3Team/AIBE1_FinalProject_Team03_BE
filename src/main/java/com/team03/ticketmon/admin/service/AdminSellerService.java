package com.team03.ticketmon.admin.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.admin.dto.AdminApprovalRequestDTO;
import com.team03.ticketmon.admin.dto.AdminRevokeRequestDTO;
import com.team03.ticketmon.admin.dto.AdminSellerApplicationListResponseDTO;
import com.team03.ticketmon.admin.dto.SellerApprovalHistoryResponseDTO;
import com.team03.ticketmon.seller_application.domain.SellerApplication;
import com.team03.ticketmon.seller_application.domain.SellerApplication.SellerApplicationStatus;
import com.team03.ticketmon.seller_application.domain.SellerApprovalHistory;
import com.team03.ticketmon.seller_application.repository.SellerApplicationRepository;
import com.team03.ticketmon.seller_application.repository.SellerApprovalHistoryRepository;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.domain.entity.UserEntity.ApprovalStatus;
import com.team03.ticketmon.user.domain.entity.UserEntity.Role;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// 콘서트 도메인 의존성 추가
import com.team03.ticketmon.concert.repository.SellerConcertRepository;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.domain.Concert;

/**
 * 관리자용 판매자 관리 비즈니스 로직 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSellerService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final UserRepository userRepository;
    private final SellerApprovalHistoryRepository sellerApprovalHistoryRepository;
    private final SellerConcertRepository sellerConcertRepository; // ConcertRepository 의존성 추가

    /**
     * API-04-01: 대기 중인 판매자 신청 목록 조회
     * @return 대기 중인 판매자 신청 목록 DTO 리스트
     */
    public List<AdminSellerApplicationListResponseDTO> getPendingSellerApplications() {
        List<SellerApplication> pendingApplications = sellerApplicationRepository.findByStatus(SellerApplicationStatus.SUBMITTED);

        return pendingApplications.stream()
                .map(AdminSellerApplicationListResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * API-04-02: 판매자 신청 승인/반려 처리
     * @param userId 처리할 판매자(유저)의 ID
     * @param request 관리자의 승인/반려 요청 정보 (approve: true/false, reason)
     * @param adminId 현재 처리하는 관리자의 ID (로그인 정보에서 추출)
     * @return 처리된 SellerApplication 엔티티
     */
    @Transactional // 상태 변경이 일어나므로 트랜잭션 필요
    public SellerApplication processSellerApplication(Long userId, AdminApprovalRequestDTO request, Long adminId) {
        // 1. 유저 및 신청서 조회
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 최신 'SUBMITTED' 상태의 신청서를 찾음 (이 신청서에 대한 처리가 필요)
        SellerApplication application = sellerApplicationRepository.findTopByUserAndStatusInOrderByCreatedAtDesc(
                        user, List.of(SellerApplicationStatus.SUBMITTED))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "대기 중인 판매자 신청서를 찾을 수 없습니다."));

        // 2. 관리자 유효성 검사 (실제 컨트롤러에서는 @PreAuthorize 등으로 먼저 처리될 수 있으나, 서비스 레벨 방어 로직)
        UserEntity adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "관리자 정보를 찾을 수 없습니다."));
        if (adminUser.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED, "관리자만 이 작업을 수행할 수 있습니다.");
        }


        // 3. 승인 또는 반려 로직
        SellerApprovalHistory.ActionType historyType; // 이력에 기록될 타입
        String historyReason = null; // 이력에 기록될 사유

        if (request.getApprove()) { // 승인 (approve = true)
            // UserEntity 업데이트
            user.setRole(Role.SELLER);
            user.setApprovalStatus(ApprovalStatus.APPROVED);
            userRepository.save(user);

            // SellerApplication 상태 업데이트
            application.setStatus(SellerApplicationStatus.ACCEPTED);
            sellerApplicationRepository.save(application);

            // 이력 타입 설정
            historyType = SellerApprovalHistory.ActionType.APPROVED;

        } else { // 반려 (approve = false)
            // 반려 사유 유효성 검사
            if (request.getReason() == null || request.getReason().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "반려 시 사유는 필수입니다.");
            }

            // UserEntity 업데이트
            user.setRole(Role.USER); // 반려 시 역할은 USER 유지 (SELLER로 가지 않음)
            user.setApprovalStatus(ApprovalStatus.REJECTED);
            // user.setLastReason(request.getReason()); // UserEntity에 lastReason 필드 없음 - 주석 처리
            userRepository.save(user);

            // SellerApplication 상태 업데이트
            application.setStatus(SellerApplicationStatus.REJECTED);
            // application.setRejectReason(request.getReason()); // SellerApplication에 반려 사유 필드 없음 - 주석 처리
            sellerApplicationRepository.save(application);

            // 이력 타입 및 사유 설정
            historyType = SellerApprovalHistory.ActionType.REJECTED;
            historyReason = request.getReason();
        }

        // 4. SellerApprovalHistory에 이력 기록 (공통)
        SellerApprovalHistory history = SellerApprovalHistory.builder()
                .user(user) // UserEntity 객체 'user' 전달
                .sellerApplication(application) // SellerApplication 객체 'application' 전달
                .type(historyType) // 이력 타입
                .reason(historyReason) // 이력 사유
                .build();
        sellerApprovalHistoryRepository.save(history);

        return application;
    }

    /**
     * API-04-03: 판매자 강제 권한 해제 (관리자)
     * @param userId 권한을 해제할 판매자(유저)의 ID
     * @param request 관리자의 강제 권한 해제 요청 정보 (reason)
     * @param adminId 현재 처리하는 관리자의 ID
     * @return 처리된 UserEntity
     */
    @Transactional
    public UserEntity revokeSellerRole(Long userId, AdminRevokeRequestDTO request, Long adminId) {
        // 1. 유저 조회
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 2. 관리자 유효성 검사
        UserEntity adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "관리자 정보를 찾을 수 없습니다."));
        if (adminUser.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED, "관리자만 이 작업을 수행할 수 있습니다.");
        }

        // 3. 강제 해제 사유 유효성 검사
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "강제 권한 해제 사유는 필수입니다.");
        }

        // 4. 판매자 권한 상태 확인 (이미 해제되었거나 일반 유저인지 등)
        if (user.getRole() != Role.SELLER && user.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "판매자 권한을 가진 사용자가 아니거나, 이미 권한이 해제된 상태입니다.");
        }

        // 5. 판매자에게 진행 중이거나 예정된 콘서트가 있는지 확인
        if (hasActiveConcertsForSeller(userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 판매자에게 진행 중이거나 예정된 콘서트가 있어 권한을 해제할 수 없습니다.");
        }

        // 6. UserEntity 업데이트 (Role을 USER로, ApprovalStatus를 REVOKED로)
        user.setRole(Role.USER);
        user.setApprovalStatus(ApprovalStatus.REVOKED);
        // user.setLastReason(request.getReason()); // UserEntity에 lastReason 필드 없음 - 주석 처리
        userRepository.save(user);

        // 6. 최신 SellerApplication의 상태도 REVOKED로 업데이트
        // 특정 UserEntity의 최신 SellerApplication을 찾아 상태를 REVOKED로 변경
        sellerApplicationRepository.findTopByUserAndStatusInOrderByCreatedAtDesc(
                        user, List.of(SellerApplicationStatus.ACCEPTED, SellerApplicationStatus.SUBMITTED)) // 현재 ACCEPTED or SUBMITTED 상태의 최신 신청서
                .ifPresent(app -> {
                    app.setStatus(SellerApplicationStatus.REVOKED);
                    sellerApplicationRepository.save(app);
                });

        // 8. SellerApprovalHistory에 REVOKED 타입 이력 기록
        SellerApprovalHistory history = SellerApprovalHistory.builder()
                .user(user)
                .sellerApplication(sellerApplicationRepository.findTopByUserOrderByCreatedAtDesc(user).orElse(null)) // 가장 최신 신청서에 연결 (없을 수도 있음)
                .type(SellerApprovalHistory.ActionType.REVOKED)
                .reason(request.getReason())
                .build();
        sellerApprovalHistoryRepository.save(history);

        return user;
    }

    /**
     * API-04-04: 판매자 권한 상세 보기 (특정 유저 이력 조회)
     * @param userId 이력을 조회할 유저의 ID
     * @param adminId 현재 처리하는 관리자의 ID
     * @return 특정 유저의 판매자 권한 이력 DTO 리스트
     */
    public List<SellerApprovalHistoryResponseDTO> getSellerApprovalHistoryForUser(Long userId, Long adminId) {
        // 1. 유저 조회 (대상 유저)
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "이력을 조회할 사용자를 찾을 수 없습니다."));

        // 2. 관리자 유효성 검사 (AdminService 내 공통 로직으로 분리도 가능)
        UserEntity adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "관리자 정보를 찾을 수 없습니다."));
        if (adminUser.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED, "관리자만 이 작업을 수행할 수 있습니다.");
        }

        // 3. 특정 유저의 모든 SellerApprovalHistory를 최신순으로 조회
        // SellerApprovalHistoryRepository에 findByUserOrderByCreatedAtDesc 메서드가 필요
        List<SellerApprovalHistory> historyList = sellerApprovalHistoryRepository.findByUserOrderByCreatedAtDesc(user);

        // 4. 엔티티 리스트를 DTO 리스트로 변환하여 반환
        return historyList.stream()
                .map(SellerApprovalHistoryResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * API-04-05: 현재 판매자 목록 조회 (관리자)
     * @return 현재 판매자(Role.SELLER)인 유저 목록 DTO 리스트
     */
    public List<AdminSellerApplicationListResponseDTO> getCurrentSellers() {
        // Role이 SELLER인 모든 UserEntity를 조회
        List<UserEntity> sellers = userRepository.findByRole(Role.SELLER);

        // UserEntity 리스트를 AdminSellerApplicationListResponseDTO 리스트로 변환하여 반환
        // 이 DTO는 신청서 정보와 User 정보를 함께 담도록 설계되었으므로,
        // 여기서는 User 정보를 기반으로 DTO를 구성하거나, 별도의 더 경량화된 DTO를 사용할 수 있음
        // 현재는 AdminSellerApplicationListResponseDTO를 재활용하여 User 정보만 채워서 반환
        return sellers.stream()
                .map(sellerUser -> AdminSellerApplicationListResponseDTO.builder()
                        .userId(sellerUser.getId())
                        .username(sellerUser.getUsername())
                        .userNickname(sellerUser.getNickname())
                        // 판매자 목록 조회이므로 companyName, businessNumber 등은 일반적으로 포함되지 않지만,
                        // 필요하다면 UserEntity나 SellerApplication에서 추가 조회하여 채울 수 있음
                        // 여기서는 일단 UserEntity가 가진 정보만 DTO에 매핑
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * API-04-06: 전체 판매자 이력 목록 조회 (검색, 정렬, 필터 포함)
     * @param typeFilter 이력 타입 필터 (예: REQUEST, APPROVED, REJECTED 등. Nullable)
     * @param keyword 검색 키워드 (유저 아이디, 닉네임, 업체명, 사업자번호, 사유 등. Nullable)
     * @param pageable 페이징 및 정렬 정보
     * @param adminId 현재 처리하는 관리자의 ID
     * @return 판매자 권한 이력 DTO 페이지
     */
    public Page<SellerApprovalHistoryResponseDTO> getAllSellerApprovalHistory(
            Optional<SellerApprovalHistory.ActionType> typeFilter,
            Optional<String> keyword,
            Pageable pageable,
            Long adminId) {

        // 1. 관리자 유효성 검사 (AdminService 내 공통 로직으로 분리도 가능)
        UserEntity adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "관리자 정보를 찾을 수 없습니다."));
        if (adminUser.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_ACCESS_DENIED, "관리자만 이 작업을 수행할 수 있습니다.");
        }

        // 2. 검색 및 필터링 로직 구현
        Page<SellerApprovalHistory> historyPage;

        // 2. 검색 및 필터링 로직 구현
        // keyword가 존재하면 keyword를 사용한 검색 메서드를 호출
        if (keyword.isPresent() && !keyword.get().trim().isEmpty()) {
            String searchKeyword = keyword.get().trim();
            if (typeFilter.isPresent()) {
                // 타입 필터와 키워드 검색을 모두 사용 (findAllWithFilters)
                historyPage = sellerApprovalHistoryRepository.findAllWithFilters(typeFilter.get(), searchKeyword, pageable);
            } else {
                // 키워드 검색만 사용 (findAllByKeywordOrderByCreatedAtDesc)
                historyPage = sellerApprovalHistoryRepository.findAllByKeywordOrderByCreatedAtDesc(searchKeyword, pageable);
            }
        } else if (typeFilter.isPresent()) {
            // 타입 필터만 사용 (findByTypeOrderByCreatedAtDesc)
            historyPage = sellerApprovalHistoryRepository.findByTypeOrderByCreatedAtDesc(typeFilter.get(), pageable);
        } else {
            // 필터/검색 조건 없이 전체 조회
            historyPage = sellerApprovalHistoryRepository.findAll(pageable);
        }

        // 3. 엔티티 페이지를 DTO 페이지로 변환하여 반환
        return historyPage.map(SellerApprovalHistoryResponseDTO::fromEntity);
    }

    /**
     * 판매자가 진행 중이거나 예정된 콘서트(ON_SALE, SCHEDULED)를 가지고 있는지 확인하는 헬퍼 메서드
     * @param sellerId 판매자 ID (UserEntity의 ID)
     * @return 활성 콘서트가 있으면 true, 없으면 false
     */
    private boolean hasActiveConcertsForSeller(Long sellerId) {
        // ON_SALE 또는 SCHEDULED 상태의 콘서트가 있는지 확인합니다.
        List<ConcertStatus> activeStatuses = List.of(ConcertStatus.ON_SALE, ConcertStatus.SCHEDULED);
        List<Concert> activeConcerts = sellerConcertRepository.findBySellerIdAndStatusIn(sellerId, activeStatuses);

        return !activeConcerts.isEmpty(); // 비어있지 않으면 활성 콘서트가 있는 것
    }
}