package com.team03.ticketmon.user.controller;

import com.team03.ticketmon.user.dto.UserProfileDTO;
import com.team03.ticketmon.user.service.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이페이지")
@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MyPageAPIController {

    private final MyPageService myPageService;

    @GetMapping("/profile")
    @Operation(summary = "사용자 프로필 조회", description = "현재 로그인된 사용자의 프로필을 조회합니다.")
    public ResponseEntity<UserProfileDTO> getUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.valueOf(userDetails.getUsername());
        UserProfileDTO userProfileDTO = myPageService.getUserProfile(userId);
        return ResponseEntity.ok(userProfileDTO);
    }
}
