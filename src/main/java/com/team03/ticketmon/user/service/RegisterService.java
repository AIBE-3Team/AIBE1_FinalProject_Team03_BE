package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.RegisterUserEntityDTO;

public interface RegisterService {
    void createUser(RegisterUserEntityDTO dto);
    RegisterResponseDTO validCheck(RegisterUserEntityDTO dto);
}
