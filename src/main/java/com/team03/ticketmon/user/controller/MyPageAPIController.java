package com.team03.ticketmon.user.controller;

import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.user.dto.UpdateUserProfileDTO;
import com.team03.ticketmon.user.dto.UserProfileDTO;
import com.team03.ticketmon.user.service.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "마이페이지")
@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MyPageAPIController {

    private final MyPageService myPageService;

    @GetMapping("/profile")
    @Operation(summary = "사용자 프로필 조회", description = "현재 로그인된 사용자의 프로필을 조회합니다.")
    public ResponseEntity<UserProfileDTO> getUserProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Long userId = userDetails.getUserId();
        UserProfileDTO userProfileDTO = myPageService.getUserProfile(userId);
        return ResponseEntity.ok(userProfileDTO);
    }

    @PostMapping("/profile")
    @Operation(summary = "사용자 프로필 변경", description = "현재 로그인된 사용자의 프로필을 변경합니다.")
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
}
