package com.team03.ticketmon.user.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.RegisterUserEntityDTO;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegisterService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;

    @Override
    public void createUser(RegisterUserEntityDTO dto, MultipartFile profileImage) {
        if (dto.email() == null || dto.username() == null || dto.password() == null)
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회원가입 필수 입력값이 누락되었습니다.");

        String profileImageUrl = userProfileService.uploadProfileAndReturnUrl(profileImage).orElse(null);

        UserEntity user = UserEntity.builder()
                .email(dto.email())
                .username(dto.username())
                .password(passwordEncoder.encode(dto.password()))
                .name(dto.name())
                .nickname(dto.nickname())
                .phone(dto.phone())
                .address(dto.address())
                .profileImage(profileImageUrl)
                .role(UserEntity.Role.USER)
                .accountStatus(UserEntity.AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
    }

    @Override
    public RegisterResponseDTO validCheck(RegisterUserEntityDTO dto) {
        if (!Boolean.TRUE.equals(dto.isSocialUser())) {
            if (dto.password() == null || !dto.password().matches("^(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=-])[A-Za-z\\d!@#$%^&*()_+=-]{8,}$")) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호는 최소 8자 이상이며, 소문자, 숫자, 특수문자를 포함해야 합니다.");
            }
        }

        return userRepository.findFirstByUsernameOrEmailOrNickname(dto.username(), dto.email(), dto.nickname())
                .map(user -> {
                    if (user.getUsername().equals(dto.username()))
                        return new RegisterResponseDTO(false, "username", "이미 사용 중인 아이디입니다.");
                    else if (user.getEmail().equals(dto.email()))
                        return new RegisterResponseDTO(false, "email", "이미 사용 중인 이메일입니다.");
                    else if (user.getNickname().equals(dto.nickname()))
                        return new RegisterResponseDTO(false, "nickname", "이미 사용 중인 닉네임입니다.");
                    return new RegisterResponseDTO(true, "", "");
                })
                .orElseGet(() -> new RegisterResponseDTO(true, "", ""));
    }
}
