package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.RegisterUserEntityDTO;
import org.springframework.web.multipart.MultipartFile;

public interface RegisterService {
    void createUser(RegisterUserEntityDTO dto, MultipartFile profileImage);
    RegisterResponseDTO validCheck(RegisterUserEntityDTO dto);
}
