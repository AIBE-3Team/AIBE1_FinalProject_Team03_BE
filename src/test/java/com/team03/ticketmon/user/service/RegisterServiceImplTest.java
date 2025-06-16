package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.RegisterResponseDTO;
import com.team03.ticketmon.user.dto.UserEntityDTO;
import com.team03.ticketmon.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("회원가입 테스트")
class RegisterServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegisterServiceImpl registerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private UserEntityDTO baseDTO() {
        return new UserEntityDTO(
                "test@email.com",
                "test",
                "1q2w3e4r!",
                "홍길동",
                "testMan",
                "010-1234-5678",
                "한국",
                ""
        );
    }

    @Test
    void 회원가입_정상처리_테스트(){
        //given
        UserEntityDTO dto = baseDTO();

        when(passwordEncoder.encode("1q2w3e4r!")).thenReturn("encodedPassword");

        // when
        registerService.createUser(dto);

        // then
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class); // 실제로 저장되는 객체를 캡처해서 검증
        verify(userRepository).save(userCaptor.capture()); // 특정 메서드가 호출됐는지 검증

        UserEntity savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(dto.email());
        assertThat(savedUser.getUsername()).isEqualTo(dto.username());
        assertThat(savedUser.getPassword()).isEqualTo("encodedPassword");
        assertThat(savedUser.getRole()).isEqualTo(UserEntity.Role.USER);
        assertThat(savedUser.getAccountStatus()).isEqualTo(UserEntity.AccountStatus.ACTIVE);
    }

    @Test
    void 회원가입_유효성검증_테스트() {
        //given
        UserEntityDTO dto = baseDTO();

        // when
        RegisterResponseDTO responseDTO = registerService.validCheck(dto);

        // then
        assertThat(responseDTO.isSuccess()).isTrue();
    }

    @Test
    void 회원가입_비밀번호_형식이_유효하지_않으면_false_반환_테스트() {
        // given
        UserEntityDTO dto = new UserEntityDTO(
                "test@email.com",
                "test",
                "qwe123",
                "홍길동",
                "testMan",
                "010-1234-5678",
                "서울특별시 서대문구 포방터2길 59(홍은동) 03607 한국",
                ""
        );

        // when
        RegisterResponseDTO responseDTO = registerService.validCheck(dto);

        //then
        assertThat(responseDTO.isSuccess()).isFalse();
    }

    @Test
    void 회원가입_중복된_아이디이면_false를_반환_테스트() {
        UserEntityDTO dto = baseDTO();

        // 해당 메서드가 호출되면 무조건 true로 반환
        when(userRepository.existsByUsername("test")).thenReturn(true);

        RegisterResponseDTO response = registerService.validCheck(dto);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.type()).isEqualTo("username");
    }

    @Test
    void 회원가입_중복된_이메일이면_false를_반환_테스트() {
        UserEntityDTO dto = baseDTO();

        when(userRepository.existsByEmail("test@email.com")).thenReturn(true);

        RegisterResponseDTO response = registerService.validCheck(dto);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.type()).isEqualTo("email");
    }

    @Test
    void 회원가입_중복된_닉네임이면_false를_반환_테스트() {
        UserEntityDTO dto = baseDTO();

        when(userRepository.existsByNickname("testMan")).thenReturn(true);

        RegisterResponseDTO response = registerService.validCheck(dto);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.type()).isEqualTo("nickname");
    }
}