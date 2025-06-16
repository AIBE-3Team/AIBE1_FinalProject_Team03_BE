package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.UserEntityDTO;

public interface RegisterService {
    void createUser(UserEntityDTO dto);
    RegisterResponseDTO validCheck(UserEntityDTO dto);
}
