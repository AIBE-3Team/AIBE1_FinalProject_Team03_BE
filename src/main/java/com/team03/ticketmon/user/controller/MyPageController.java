package com.team03.ticketmon.user.controller;

import com.team03.ticketmon.user.dto.UserProfileDTO;
import com.team03.ticketmon.user.service.MyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Long userId = Long.valueOf(userDetails.getUsername());
        UserProfileDTO userProfileDTO = myPageService.getUserProfile(userId);
        model.addAttribute("user", userProfileDTO);
        return "mypage/profile";
    }
}
