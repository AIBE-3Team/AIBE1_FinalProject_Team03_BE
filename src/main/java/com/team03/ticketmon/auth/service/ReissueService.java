package com.team03.ticketmon.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ReissueService {
    String reissueAccessToken(String refreshToken);
    String reissueRefreshToken(String refreshToken);
    void handleReissueToken(HttpServletRequest request, HttpServletResponse response);
}
