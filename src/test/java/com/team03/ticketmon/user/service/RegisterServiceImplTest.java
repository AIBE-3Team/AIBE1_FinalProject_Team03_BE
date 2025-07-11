package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.RegisterUserEntityDTO;
import com.team03.ticketmon.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원가입 테스트")
class RegisterServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private RegisterServiceImpl registerService;

    private RegisterUserEntityDTO baseDTO;
    private MultipartFile mockFile;
    private UserEntity duplicateUser;

    @BeforeEach
    void setUp() {
        mockFile = mock(MultipartFile.class);
        baseDTO = new RegisterUserEntityDTO(
                "test@email.com",
                "test",
                "1q2w3e4r!",
                "홍길동",
                "testMan",
                "010-1234-5678",
                "한국"
        );
    }

    @Test
    void 회원가입_정상처리_테스트() {
        // given
        given(passwordEncoder.encode("1q2w3e4r!")).willReturn("encodedPassword");
        given(userProfileService.uploadProfileAndReturnUrl(mockFile))
                .willReturn(Optional.of("http://NewProfile.png"));

        // when
        registerService.createUser(baseDTO, mockFile);

        // then
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class); // 실제로 저장되는 객체를 캡처해서 검증
        verify(userRepository).save(userCaptor.capture()); // 특정 메서드가 호출됐는지 검증

        UserEntity savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(baseDTO.email());
        assertThat(savedUser.getUsername()).isEqualTo(baseDTO.username());
        assertThat(savedUser.getPassword()).isEqualTo("encodedPassword");
        assertThat(savedUser.getRole()).isEqualTo(UserEntity.Role.USER);
        assertThat(savedUser.getAccountStatus()).isEqualTo(UserEntity.AccountStatus.ACTIVE);
        assertThat(savedUser.getProfileImage()).isEqualTo("http://NewProfile.png");
    }

    @Test
    void 회원가입_유효성검증_성공_테스트() {
        // given
        given(userRepository.findFirstByUsernameOrEmailOrNickname(any(), any(), any()))
                .willReturn(Optional.empty());

        // when
        RegisterResponseDTO responseDTO = registerService.validCheck(baseDTO);

        // then
        assertThat(responseDTO.isSuccess()).isTrue();
    }

    @Test
    void 회원가입_중복된_아이디이면_false를_반환_테스트() {
        // 해당 메서드가 호출되면 무조건 true로 반환
        duplicateUser = UserEntity.builder().username("test").email("other@email.com").nickname("other").build();

        given(userRepository.findFirstByUsernameOrEmailOrNickname(baseDTO.username(), baseDTO.email(), baseDTO.nickname())).willReturn(Optional.of(duplicateUser));

        RegisterResponseDTO response = registerService.validCheck(baseDTO);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.type()).isEqualTo("username");
    }

    @Test
    void 회원가입_중복된_이메일이면_false를_반환_테스트() {
        duplicateUser = UserEntity.builder().username("other").email("test@email.com").nickname("other").build();

        given(userRepository.findFirstByUsernameOrEmailOrNickname(baseDTO.username(), baseDTO.email(), baseDTO.nickname())).willReturn(Optional.of(duplicateUser));

        RegisterResponseDTO response = registerService.validCheck(baseDTO);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.type()).isEqualTo("email");
    }

    @Test
    void 회원가입_중복된_닉네임이면_false를_반환_테스트() {
        duplicateUser = UserEntity.builder().username("other").email("other@email.com").nickname("testMan").build();

        given(userRepository.findFirstByUsernameOrEmailOrNickname(baseDTO.username(), baseDTO.email(), baseDTO.nickname())).willReturn(Optional.of(duplicateUser));

        RegisterResponseDTO response = registerService.validCheck(baseDTO);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.type()).isEqualTo("nickname");
    }
}