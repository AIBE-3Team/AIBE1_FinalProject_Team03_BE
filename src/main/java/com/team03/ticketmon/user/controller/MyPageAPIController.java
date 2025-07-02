package com.team03.ticketmon.user.controller;

import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.user.dto.*;
import com.team03.ticketmon.user.service.MyBookingService;
import com.team03.ticketmon.user.service.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "마이페이지")
@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MyPageAPIController {

    private final MyPageService myPageService;
    private final MyBookingService myBookingService;

    @GetMapping("/profile")
    @Operation(summary = "사용자 프로필 조회", description = "현재 로그인된 사용자의 프로필을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "프로필 조회 성공")
    @ApiResponse(responseCode = "401", description = "사용자 권한 인증 실패")
    public ResponseEntity<UserProfileDTO> getUserProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Long userId = userDetails.getUserId();
        UserProfileDTO userProfileDTO = myPageService.getUserProfile(userId);
        return ResponseEntity.ok(userProfileDTO);
    }

    @PostMapping("/profile")
    @Operation(summary = "사용자 프로필 변경", description = "현재 로그인된 사용자의 프로필을 변경합니다.")
    @ApiResponse(responseCode = "200", description = "프로필 변경 성공")
    @ApiResponse(responseCode = "400", description = "프로필 형식 불일치")
    @ApiResponse(responseCode = "401", description = "사용자 권한 인증 실패")
    public ResponseEntity<?> updateUserProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Validated @ModelAttribute("user") UpdateUserProfileDTO dto,
            BindingResult bindingResult) {

        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (bindingResult.hasErrors())
            return ResponseEntity.badRequest().body(bindingResult.getAllErrors());

        Long userId = userDetails.getUserId();
        myPageService.updateUserProfile(userId, dto);
        UserProfileDTO updatedProfile = myPageService.getUserProfile(userId);

        return ResponseEntity.ok(updatedProfile);
    }

    @PostMapping("/changePwd")
    @Operation(summary = "사용자 비밀번호 변경", description = "현재 로그인된 사용자의 비밀번호를 변경합니다. 소문자, 숫자, 특수문자 포함, 8자 이상")
    @ApiResponse(responseCode = "200", description = "비밀번호 변경 성공")
    @ApiResponse(responseCode = "400", description = "비밀번호 형식 불일치")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Validated UpdatePasswordDTO dto,
            BindingResult bindingResult) {

        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (bindingResult.hasErrors())
            return ResponseEntity.badRequest().body(bindingResult.getAllErrors());

        try {
            Long userId = userDetails.getUserId();
            myPageService.updatePassword(userId, dto);
            return ResponseEntity.ok("비밀번호 변경 성공");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/booking")
    @Operation(summary = "사용자 예매 내역 조회", description = "현재 로그인된 사용자의 예매 내역을 불러옵니다.")
    public ResponseEntity<?> getBookingList(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<UserBookingSummaryDTO> booking = myBookingService.findBookingList(userDetails.getUserId());

        return ResponseEntity.ok().body(booking);
    }

    @GetMapping("/bookingDetail/{bookingNumber}")
    @Operation(summary = "사용자 예매 상세 내역 조회", description = "현재 로그인된 사용자의 예매 상세 내역을 불러옵니다.")
    public ResponseEntity<?> getBookingDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String bookingNumber) {

        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        UserBookingDetailDto bookingDto = myBookingService.findBookingDetail(userDetails.getUserId(), bookingNumber);

        return ResponseEntity.ok().body(bookingDto);
    }

    @DeleteMapping("/bookingDetail/cancel/{bookingId}")
    @Operation(summary = "사용자 예매 취소", description = "현재 로그인된 사용자의 예매를 취소합니다.")
    public ResponseEntity<?> cancelBooking(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long bookingId) {

        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        myBookingService.cancelBooking(userDetails.getUserId(), bookingId);

        return ResponseEntity.ok().body("예매가 성공적으로 취소되었습니다.");
    }
}
