package com.team03.ticketmon.auth.controller;

import com.team03.ticketmon.auth.dto.LoginDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
}
