package com.team03.ticketmon.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ReissueService {
    String reissueToken(String refreshToken, String reissueCategory, boolean dbCheck);
    void handleReissueToken(HttpServletRequest request, HttpServletResponse response);
}
