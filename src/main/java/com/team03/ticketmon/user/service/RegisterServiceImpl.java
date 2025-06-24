package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.RegisterUserEntityDTO;
import com.team03.ticketmon.user.dto.UserEntityDTO;
import com.team03.ticketmon.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class RegisterServiceImpl implements RegisterService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void createUser(RegisterUserEntityDTO dto) {

        UserEntity user = UserEntity.builder()
                .email(dto.email())
                .username(dto.username())
                .password(passwordEncoder.encode(dto.password()))
                .name(dto.name())
                .nickname(dto.nickname())
                .phone(dto.phone())
                .address(dto.address())
                .role(UserEntity.Role.USER)
                .accountStatus(UserEntity.AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
    }

    @Override
    public RegisterResponseDTO validCheck(RegisterUserEntityDTO dto) {

        if (userRepository.existsByUsername(dto.username()))
            return new RegisterResponseDTO(false, "username", "이미 사용 중인 아이디입니다.");

        if (userRepository.existsByEmail(dto.email()))
            return new RegisterResponseDTO(false, "email", "이미 사용 중인 이메일입니다.");
        ;

        if (userRepository.existsByNickname(dto.nickname()))
            return new RegisterResponseDTO(false, "nickname", "이미 사용 중인 닉네임입니다.");
        ;

        return new RegisterResponseDTO(true, "", "");
    }
}
