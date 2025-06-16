package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.UserDTO;

public interface RegisterService {
    void createUser(UserDTO dto);
    RegisterResponseDTO validCheck(UserDTO dto);
}
