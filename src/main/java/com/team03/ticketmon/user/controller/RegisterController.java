package com.team03.ticketmon.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/auth")
public class RegisterController {

    @GetMapping("/register")
    public String register() {
        return "auth/register";
    }
}
