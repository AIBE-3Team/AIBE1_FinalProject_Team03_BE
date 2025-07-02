package com.team03.ticketmon.auth.controller;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.dto.RegisterSocialDTO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class OauthAPIController {

    @GetMapping("/oauthAttributes")
    @Operation(summary = "소셜 사용자 정보 전달", description = "신규 유저의 소셜 로그인시 정보를 전달하여 회원가입을 유도합니다.")
    public ResponseEntity<?> getOauthAttributes(HttpSession session) {
        OAuthAttributes attr = (OAuthAttributes) session.getAttribute("oauthAttributes");

        if (attr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었거나 정보가 없습니다.");
        }

        RegisterSocialDTO socialDTO = new RegisterSocialDTO(
                attr.getEmail(),
                attr.getName()
        );

        return ResponseEntity.ok().body(socialDTO);
    }
}
