package com.team03.ticketmon.seller_application.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.exception.BusinessException;

import com.team03.ticketmon.auth.jwt.CustomUserDetails; // 로그인 유저 정보 주입

// import com.team03.ticketmon.user.domain.entity.UserEntity; // 이 줄을 삭제
// import com.team03.ticketmon.user.repository.UserRepository; // 이 줄을 삭제

import com.team03.ticketmon.seller_application.dto.ApplicantInformationResponseDTO;
import com.team03.ticketmon.seller_application.dto.SellerApplicationRequestDTO;
import com.team03.ticketmon.seller_application.dto.SellerApplicationStatusResponseDTO;
import com.team03.ticketmon.seller_application.service.SellerApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid; // @Valid 어노테이션 사용
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // MultipartFile 요청에 필요
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 로그인 유저 정보 주입
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // 파일 업로드에 필요


/**
 * 판매자 권한 신청 및 상태 조회 관련 API를 처리하는 컨트롤러입니다.
 * 모든 API는 로그인된 사용자만 접근 가능합니다.
 */
@Tag(name = "판매자 권한 신청 API", description = "사용자의 판매자 권한 신청 및 관리 (사용자 측면)")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationService sellerApplicationService; // 서비스 계층 주입
    // private final UserRepository userRepository; // UserEntity 조회용

    /**
     * API-03-05: 로그인 사용자의 현재 판매자 권한 상태 조회
     * @param userDetails 인증된 사용자 정보 (Spring Security)
     * @return 판매자 권한 상태 응답 DTO
     */
    @Operation(
            summary = "로그인 사용자의 판매자 권한 상태 조회",
            description = "현재 로그인된 사용자의 판매자 권한 상태를 조회합니다. 이 상태에 따라 UI의 버튼 활성화 여부가 결정됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "판매자 권한 상태 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음") // 서비스 계층에서 발생 가능
    })
    @GetMapping("/seller-status")
    public ResponseEntity<SuccessResponse<SellerApplicationStatusResponseDTO>> getSellerStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) { // userId 추출

        // Spring Security가 인증 처리중
        // if (userDetails == null) {
        //    throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "로그인이 필요합니다.");
        // }
        Long userId = userDetails.getUserId();

        SellerApplicationStatusResponseDTO status = sellerApplicationService.getSellerApplicationStatus(userId);
        return ResponseEntity.ok(SuccessResponse.of("판매자 권한 상태 조회 성공", status));
    }


    /**
     * API-03-06: 판매자 권한 신청 등록/재신청
     * MultipartFile을 포함하므로 @PostMapping의 consumes 속성 설정이 필요합니다.
     * @param request 판매자 신청 정보 DTO
     * @param document 제출 서류 파일
     * @param userDetails 인증된 사용자 정보
     * @return 성공 응답 (상태 코드 201 Created)
     */
    @Operation(
            summary = "판매자 권한 신청 또는 재신청",
            description = "일반 사용자가 판매자 권한을 신청하거나, 반려된 경우 재신청합니다. 사업자등록증 등의 서류 파일을 함께 제출합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "판매자 권한 신청 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력값 또는 파일 형식/크기 문제"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "409", description = "이미 신청이 진행 중이거나 판매자 권한을 보유함")
    })
    @PostMapping(value = "/seller-requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) // MultipartFile 요청을 위한 consumes 설정
    public ResponseEntity<SuccessResponse<Void>> applyForSeller(
            @Parameter(description = "판매자 신청 정보", required = true)
            @Valid @RequestPart("request") SellerApplicationRequestDTO request, // DTO는 @RequestPart로 받도록 설정
            @Parameter(description = "제출 서류 파일 (사업자등록증 등)", required = true)
            @RequestPart("document") MultipartFile document, // 파일은 @RequestPart로 받도록 설정
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {

        // Spring Security가 인증 처리중
        // if (userDetails == null) {
        //    throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "로그인이 필요합니다.");
        // }
        Long userId = userDetails.getUserId();

        sellerApplicationService.applyForSeller(userId, request, document);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.of("판매자 권한 신청이 성공적으로 접수되었습니다.", null));
    }

    /**
     * API-03-07: 판매자 본인 권한 철회
     * @param userDetails 인증된 사용자 정보
     * @return 성공 응답 (상태 코드 200 OK)
     */
    @Operation(
            summary = "판매자 권한 자발적 철회",
            description = "현재 판매자 권한을 가진 사용자가 자신의 권한을 자발적으로 철회합니다. 진행 중인 콘서트가 없는 경우에만 가능합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "판매자 권한 철회 성공"),
            @ApiResponse(responseCode = "400", description = "콘서트가 남아있어 철회 불가 등 잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "판매자 권한이 없거나 승인 상태가 아님"),  // 서비스 계층에서 발생 가능
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")    // 서비스 계층에서 발생 가능
    })
    @DeleteMapping("/role") // DELETE 메서드 사용
    public ResponseEntity<SuccessResponse<Void>> withdrawSellerRole(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();

        sellerApplicationService.withdrawSellerRole(userId);
        return ResponseEntity.ok(SuccessResponse.of("판매자 권한이 성공적으로 철회되었습니다.", null));
    }

    // --- 새로 추가된 엔드포인트 ---
    /**
     * API: 현재 로그인된 사용자 신청자 정보 조회(API-03-08)
     * GET /api/users/me/applicant-info
     * AuthContext에서 사용할 사용자 상세 정보를 제공합니다.
     * @param userDetails Spring Security의 인증된 사용자 정보
     * @return ApplicantInformationResponseDTO를 포함한 성공 응답
     */
    @Operation(
            summary = "현재 사용자 신청자 정보 조회",
            description = "로그인된 사용자의 신청자 양식에 필요한 상세 프로필 정보를 조회합니다. (AuthContext 업데이트용)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/applicant-info") // 실제 엔드포인트는 /api/users/me/applicant-info가 됨
    public ResponseEntity<SuccessResponse<ApplicantInformationResponseDTO>> getApplicantInformation(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "로그인이 필요합니다.");
        }

        // 기존 userRepository 직접 호출 부분을 sellerApplicationService 호출로 변경
        ApplicantInformationResponseDTO responseDTO = sellerApplicationService.getUserApplicantInfo(userDetails.getUserId());

        return ResponseEntity.ok(SuccessResponse.of("현재 사용자 신청자 정보 조회 성공", responseDTO));
    }
}