package com.team03.ticketmon.auth.controller;

import com.team03.ticketmon.auth.dto.LoginDTO;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.user.dto.UserResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "로그인")
public class loginAPIController {

    @PostMapping("/login")
    @Operation(
            summary = "로그인",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/x-www-form-urlencoded",
                            schema = @Schema(implementation = LoginDTO.class)
                    )
            )
    )
    public void login() {
        // UsernamePasswordAuthenticationFilter가 가로채므로 실제 구현은 생략
    }

    @GetMapping("/me")
    @Operation(summary = "로그인된 사용자 인증 정보 전달", description = "현재 로그인된 사용자의 인증 정보를 전달합니다.")
    @ApiResponse(responseCode = "200", description = "사용자 인증 정보 전달 완료")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {

        CustomUserDetails userDetails = extractUserDetails(authentication);

        if (userDetails == null)
            return ResponseEntity.ok().body(null);

        String role = userDetails.getAuthorities().isEmpty() ?
                "UNKNOWN" : userDetails.getAuthorities().iterator().next().getAuthority();

        UserResponseDTO dto = new UserResponseDTO(
                userDetails.getUserId(),
                userDetails.getUsername(),
                userDetails.getNickname(),
                role
        );

        return ResponseEntity.ok(dto);
    }

    private CustomUserDetails extractUserDetails(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        return null;
    }
}
