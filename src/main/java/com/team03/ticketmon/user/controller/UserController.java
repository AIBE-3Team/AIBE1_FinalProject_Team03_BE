package com.team03.ticketmon.user.controller;

import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.UserEntityDTO;
import com.team03.ticketmon.user.service.RegisterService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
public class UserController {

    private final RegisterService registerService;

    public UserController(RegisterService registerService) {
        this.registerService = registerService;
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerProcess(@RequestBody UserEntityDTO dto) {
        RegisterResponseDTO validation = registerService.validCheck(dto);

        if(!validation.isSuccess())
            return ResponseEntity.badRequest().body(validation);

        registerService.createUser(dto);

        return ResponseEntity.ok().body(validation);
    }

}
