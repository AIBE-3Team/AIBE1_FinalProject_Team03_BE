package com.team03.ticketmon.user.controller;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.RegisterSocialDTO;
import com.team03.ticketmon.user.dto.RegisterUserEntityDTO;
import com.team03.ticketmon.user.service.RegisterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "회원가입", description = "유저 회원가입 관련 API입니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class RegisterAPIController {

    private final RegisterService registerService;

    @GetMapping("/register/social")
    @Operation(summary = "소셜 로그인 정보 조회", description = "세션에 저장된 소셜 사용자 정보를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "소셜 사용자 정보 반환 성공")
    public ResponseEntity<?> registerInfo(HttpSession session) {
        OAuthAttributes attr = (OAuthAttributes) session.getAttribute("oauthAttributes");

        if (attr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 정보가 없습니다.");
        }

        RegisterSocialDTO socialDTO = new RegisterSocialDTO(
                attr.getEmail() != null ? attr.getEmail() : "",
                attr.getName() != null ? attr.getName() : ""
        );

        if (socialDTO.email().isEmpty() || socialDTO.name().isEmpty())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("소셜 유저 정보가 누락되었습니다.");

        return ResponseEntity.ok().body(socialDTO);
    }

    @PostMapping("/register")
    @Operation(summary = "회원가입 요청",
            description = "사용자 정보를 바탕으로 회원가입을 처리합니다. 비밀번호 : 영어 소문자, 숫자, 특수 문자 조합 8자 이상")
    @ApiResponse(responseCode = "200", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "유효성 검사 실패")
    public ResponseEntity<?> registerProcess(
            @Validated @ModelAttribute RegisterUserEntityDTO dto,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
            BindingResult bindingResult) {

        RegisterResponseDTO validation = registerService.validCheck(dto);

        if (bindingResult.hasErrors())
            return ResponseEntity.badRequest().body(bindingResult.getAllErrors());

        if (!validation.isSuccess())
            return ResponseEntity.badRequest().body(validation);

        registerService.createUser(dto, profileImage);
        return ResponseEntity.ok().body(validation);
    }
}
