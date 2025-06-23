package com.team03.ticketmon.auth.controller;

import com.team03.ticketmon.auth.dto.LoginDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "로그인")
public class loginAPIController {

    @Operation(summary = "로그인 (Swagger 문서용)", description = "실제 로그인을 처리하지 않고, 요청 형식만 문서화합니다. 실제 로그인은 필터에서 처리됨")
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginDTO dto) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("실제 로그인은 /auth/login 필터에서 처리됨");
    }
}
