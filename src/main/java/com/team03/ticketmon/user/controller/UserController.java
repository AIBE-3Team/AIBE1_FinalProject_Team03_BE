package com.team03.ticketmon.user.controller;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.UserEntityDTO;
import com.team03.ticketmon.user.service.RegisterService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class UserController {

    private final RegisterService registerService;

    public UserController(RegisterService registerService) {
        this.registerService = registerService;
    }

    @GetMapping("/auth/register")
    public String register() {
        return "register.html";
    }

    @GetMapping("/auth/register/social")
    @ResponseBody
    public Map<String, Object> registerInfo(HttpSession session) {
        OAuthAttributes attr = (OAuthAttributes) session.getAttribute("oauthAttributes");
        Map<String, Object> result = new HashMap<>();
        if (attr != null) {
            result.put("isSocial", true);
            result.put("name", attr.getName());
            result.put("email", attr.getEmail());
        } else {
            result.put("isSocial", false);
        }
        return result;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> registerProcess(@RequestBody UserEntityDTO dto) {
        RegisterResponseDTO validation = registerService.validCheck(dto);

        if (!validation.isSuccess())
            return ResponseEntity.badRequest().body(validation);

        registerService.createUser(dto);
        return ResponseEntity.ok().body(validation);
    }
}
