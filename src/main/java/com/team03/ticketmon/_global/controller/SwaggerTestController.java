package com.team03.ticketmon._global.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//@Hidden   // Swagger에 표시되지 않도록 숨기기 위한 어노테이션
@RestController
@RequestMapping("/test")  // 공통 prefix
public class SwaggerTestController {

    // 사용자 API 테스트
    @GetMapping("/user")
    public String userTest() {
        return "사용자 API 테스트 성공!";
    }

    // 관리자 API 테스트
    @PreAuthorize("hasRole('ADMIN')") // ← JWT 구현 완료 후 주석 해제 제안
    @GetMapping("/admin")
    public String adminTest() {
        return "관리자 API 테스트 성공!";
    }
}