package com.team03.ticketmon.admin.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.admin.dto.AdminApprovalRequestDTO;
import com.team03.ticketmon.admin.dto.AdminRevokeRequestDTO;
import com.team03.ticketmon.admin.dto.AdminSellerApplicationListResponseDTO;
import com.team03.ticketmon.admin.dto.SellerApprovalHistoryResponseDTO;
import com.team03.ticketmon.admin.service.AdminSellerService;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.BindingResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import com.team03.ticketmon._global.validation.OnReject;

import com.team03.ticketmon._global.exception.ErrorResponse;
import com.team03.ticketmon._global.exception.ErrorCode;

import com.team03.ticketmon.seller_application.domain.SellerApprovalHistory;


/**
 * 관리자용 판매자 관리 API 컨트롤러
 * 모든 API는 ADMIN 권한이 필요
 */
@Tag(name = "관리자 판매자 관리", description = "관리자 전용 판매자 신청 및 권한 관리 API")
@RestController
@RequestMapping("/api/admin") // 공통 최상위 경로로 변경 (하위 경로에서 seller-requests, sellers 구분)
@RequiredArgsConstructor
public class AdminSellerController {

    private final AdminSellerService adminSellerService;
    private final Validator validator;

    /**
     * API-04-01: 대기 중인 판매자 신청 목록 조회
     * @param adminUser 현재 로그인된 관리자 정보 (권한 검증용)
     * @return 대기 중인 판매자 신청 목록 DTO 리스트
     */
    @Operation(
            summary = "대기 중인 판매자 신청 목록 조회",
            description = "관리자가 승인 대기 중인 판매자 신청 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN만 접근 가능)")
    })
    @GetMapping("/seller-requests") // 변경된 RequestMapping에 맞춰 경로 명시
    @PreAuthorize("hasRole('ADMIN')") // ADMIN 역할만 접근 허용
    public ResponseEntity<SuccessResponse<List<AdminSellerApplicationListResponseDTO>>> getPendingApplications(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUser) {

        // 현재 컨트롤러에서 @PreAuthorize를 통해 ADMIN 역할 검증
        // TODO: (선택) 서비스 계층에서 adminUser.getUserId()를 사용하여 관리자 권한을 최종적으로 검증 또는 로깅
        // 현재 AdminSellerService.getPendingApplications()는 adminId 파라미터를 받지 않으므로,
        // 이를 위해 서비스 메서드 시그니처 변경 및 추가 로직 구현이 필요할 수 있음
        List<AdminSellerApplicationListResponseDTO> pendingApplications = adminSellerService.getPendingSellerApplications();

        return ResponseEntity.ok(SuccessResponse.of("대기 중인 판매자 신청 목록 조회 성공", pendingApplications));
    }

    /**
     * API-04-02: 판매자 신청 승인/반려 처리
     * @param userId 처리할 판매자(유저)의 ID
     * @param request 관리자의 승인/반려 요청 정보 (approve: true/false, reason)
     * @param adminUser 현재 로그인된 관리자 정보
     * @return 처리 결과에 대한 성공 응답 (실제로는 처리된 신청서 정보 포함 가능)
     */
    @Operation(
            summary = "판매자 신청 승인/반려 처리",
            description = "관리자가 특정 판매자 신청을 승인하거나 반려합니다. 반려 시 사유를 필수로 포함해야 합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (사유 누락 등)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "신청서 또는 사용자 정보 없음")
    })
    @PatchMapping("/seller-requests/{userId}/process") // 변경된 RequestMapping에 맞춰 경로 명시
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> processSellerApplication( // 반환 타입을 SuccessResponse<String>에서 ?로 변경
                                                       @Parameter(description = "처리할 판매자(유저) ID", example = "1")
                                                       @PathVariable Long userId,
                                                       @Parameter(description = "승인/반려 요청 정보", required = true)
                                                       @Valid @RequestBody AdminApprovalRequestDTO request,
                                                       BindingResult bindingResult,
                                                       @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUser) {

        // 1. @Valid (기본 그룹) 검증 결과 확인
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(
                    ErrorResponse.of(ErrorCode.INVALID_INPUT, bindingResult)
            );
        }

        // 2. approve가 false(반려)일 경우에만 reason 필드에 대한 추가 유효성 검사 수행
        if (Boolean.FALSE.equals(request.getApprove())) { // approve가 false인 경우
            // OnReject 그룹에 속한 유효성 검사를 수동으로 수행
            Set<ConstraintViolation<AdminApprovalRequestDTO>> violations = validator.validate(request, OnReject.class);
            if (!violations.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ErrorResponse.of(ErrorCode.INVALID_INPUT, violations.iterator().next().getMessage())
                );
            }
        }

        // 서비스 계층에서 adminUser.getUserId()를 사용하여 관리자 권한을 검증하고 처리
        adminSellerService.processSellerApplication(userId, request, adminUser.getUserId());

        String message = request.getApprove() ? "판매자 신청이 승인되었습니다." : "판매자 신청이 반려되었습니다.";
        return ResponseEntity.ok(SuccessResponse.of(message, "SUCCESS"));
    }

    /**
     * API-04-03: 판매자 강제 권한 해제 (관리자)
     * @param userId 권한을 해제할 판매자(유저)의 ID
     * @param request 관리자의 강제 권한 해제 요청 정보 (reason)
     * @param adminUser 현재 로그인된 관리자 정보
     * @return 처리 결과에 대한 성공 응답 (실제로는 처리된 유저 정보 포함 가능)
     */
    @Operation(
            summary = "판매자 강제 권한 해제",
            description = "관리자가 특정 판매자의 권한을 강제로 해제합니다. 해제 사유를 필수로 포함해야 합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (사유 누락 등)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 정보 없음")
    })
    @DeleteMapping("/sellers/{userId}/role") // 변경된 RequestMapping에 맞춰 경로 명시
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<String>> revokeSellerRole(
            @Parameter(description = "권한 해제할 판매자(유저) ID", example = "1")
            @PathVariable Long userId,
            @Parameter(description = "강제 권한 해제 요청 정보", required = true)
            @Valid @RequestBody AdminRevokeRequestDTO request, // DELETE 요청에 @RequestBody 가능 (Spring 4.3+, RESTful 관례상 POST/PATCH 선호)
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUser) {

        adminSellerService.revokeSellerRole(userId, request, adminUser.getUserId());

        return ResponseEntity.ok(SuccessResponse.of("판매자 권한이 강제로 해제되었습니다.", "SUCCESS"));
    }

    /**
     * API-04-04: 판매자 권한 상세 보기 (특정 유저 이력 조회)
     * @param userId 이력을 조회할 유저의 ID
     * @param adminUser 현재 로그인된 관리자 정보
     * @return 특정 유저의 판매자 권한 이력 DTO 리스트
     */
    @Operation(
            summary = "특정 유저의 판매자 권한 이력 조회",
            description = "관리자가 특정 유저의 판매자 권한 신청 및 변경 이력을 상세 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 정보 없음")
    })
    @GetMapping("/sellers/{userId}/approval-history") // API-04-04 명세에 맞춰 경로 수정
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<List<SellerApprovalHistoryResponseDTO>>> getSellerApprovalHistoryForUser(
            @Parameter(description = "이력을 조회할 유저 ID", example = "1")
            @PathVariable Long userId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUser) {

        List<SellerApprovalHistoryResponseDTO> history = adminSellerService.getSellerApprovalHistoryForUser(userId, adminUser.getUserId());

        return ResponseEntity.ok(SuccessResponse.of("특정 유저의 판매자 권한 이력 조회 성공", history));
    }

    /**
     * API-04-05: 현재 판매자 목록 조회 (관리자)
     * @param adminUser 현재 로그인된 관리자 정보
     * @return 현재 판매자(Role.SELLER)인 유저 목록 DTO 리스트
     */
    @Operation(
            summary = "현재 판매자 목록 조회",
            description = "관리자가 현재 판매자 권한을 가진 유저 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @GetMapping("/sellers") // API-04-05 명세에 맞춰 경로 추가
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<List<AdminSellerApplicationListResponseDTO>>> getCurrentSellers(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUser) {

        // TODO: (선택) 서비스 계층에서 adminUser.getUserId()를 사용하여 관리자 권한을 최종적으로 검증
        // 현재 AdminSellerService.getCurrentSellers()는 adminId 파라미터를 받지 않으므로,
        // 이를 위해 서비스 메서드 시그니처 변경 및 추가 로직 구현이 필요할 수 있음
        List<AdminSellerApplicationListResponseDTO> currentSellers = adminSellerService.getCurrentSellers();

        return ResponseEntity.ok(SuccessResponse.of("현재 판매자 목록 조회 성공", currentSellers));
    }

    /**
     * API-04-06: 전체 판매자 이력 목록 조회 (관리자)
     * @param typeFilter 이력 타입 필터 (예: REQUEST, APPROVED, REJECTED 등. Nullable)
     * @param keyword 검색 키워드 (유저 아이디, 닉네임, 업체명, 사업자번호, 사유 등. Nullable)
     * @param pageable 페이징 및 정렬 정보
     * @param adminUser 현재 로그인된 관리자 정보
     * @return 판매자 권한 이력 DTO 페이지
     */
    @Operation(
            summary = "전체 판매자 이력 목록 조회",
            description = "관리자가 모든 판매자 권한 이력을 검색, 필터링, 정렬하여 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @GetMapping("/approvals/history") // API-04-06 명세에 맞춰 경로 추가
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<Page<SellerApprovalHistoryResponseDTO>>> getAllSellerApprovalHistory(
            @Parameter(description = "이력 타입 필터 (예: SUBMITTED, APPROVED, REJECTED, REVOKED)", example = "APPROVED")
            @RequestParam(required = false) Optional<SellerApprovalHistory.ActionType> typeFilter,
            @Parameter(description = "검색 키워드 (유저 ID, 닉네임 등)", example = "seller1")
            @RequestParam(required = false) Optional<String> keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUser) {

        Page<SellerApprovalHistoryResponseDTO> historyPage = adminSellerService.getAllSellerApprovalHistory(
                typeFilter, keyword, pageable, adminUser.getUserId());

        return ResponseEntity.ok(SuccessResponse.of("전체 판매자 이력 목록 조회 성공", historyPage));
    }
}