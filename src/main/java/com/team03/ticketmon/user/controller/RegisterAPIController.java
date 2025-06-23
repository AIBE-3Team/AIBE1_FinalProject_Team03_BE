package com.team03.ticketmon.user.controller;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.SocialRegisterDTO;
import com.team03.ticketmon.user.dto.UserEntityDTO;
import com.team03.ticketmon.user.service.RegisterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원가입", description = "유저 회원가입 관련 API입니다.")
@RestController
@RequestMapping("/api/auth")
public class RegisterAPIController {

    private final RegisterService registerService;

    public RegisterAPIController(RegisterService registerService) {
        this.registerService = registerService;
    }

    @GetMapping("/register/social")
    @Operation(summary = "소셜 로그인 정보 조회", description = "세션에 저장된 소셜 사용자 정보를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "소셜 사용자 정보 반환 성공")
    public SocialRegisterDTO registerInfo(HttpSession session) {
        OAuthAttributes attr = (OAuthAttributes) session.getAttribute("oauthAttributes");
        if (attr != null) {
            return new SocialRegisterDTO(true, attr.getName(), attr.getEmail());
        } else {
            return new SocialRegisterDTO(false, null, null);
        }
    }

    @PostMapping("/register")
    @Operation(summary = "회원가입 요청", description = "사용자 정보를 바탕으로 회원가입을 처리합니다.")
    @ApiResponse(responseCode = "200", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "유효성 검사 실패")
    public ResponseEntity<?> registerProcess(@RequestBody UserEntityDTO dto) {
        RegisterResponseDTO validation = registerService.validCheck(dto);

        if (!validation.isSuccess())
            return ResponseEntity.badRequest().body(validation);

        registerService.createUser(dto);
        return ResponseEntity.ok().body(validation);
    }
}
