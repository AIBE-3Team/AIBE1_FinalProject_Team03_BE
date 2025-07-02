package com.team03.ticketmon.user.service;

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
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegisterService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;

    @Override
    public void createUser(RegisterUserEntityDTO dto, MultipartFile profileImage) {

        String profileImageUrl = userProfileService.uploadProfileAndReturnUrl(profileImage);

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
        Optional<UserEntity> existingUser = userRepository
                .findFirstByUsernameOrEmailOrNickname(dto.username(), dto.email(), dto.nickname());

        if (existingUser.isPresent()) {
            UserEntity user = existingUser.get();

            if (user.getUsername().equals(dto.username())) {
                return new RegisterResponseDTO(false, "username", "이미 사용 중인 아이디입니다.");
            }
            if (user.getEmail().equals(dto.email())) {
                return new RegisterResponseDTO(false, "email", "이미 사용 중인 이메일입니다.");
            }
            if (user.getNickname().equals(dto.nickname())) {
                return new RegisterResponseDTO(false, "nickname", "이미 사용 중인 닉네임입니다.");
            }
        }

        return new RegisterResponseDTO(true, "", "");
    }
}
