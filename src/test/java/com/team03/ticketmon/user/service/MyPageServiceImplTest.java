package com.team03.ticketmon.user.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.UpdatePasswordDTO;
import com.team03.ticketmon.user.dto.UpdateUserProfileDTO;
import com.team03.ticketmon.user.dto.UserProfileDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("마이페이지 테스트")
class MyPageServiceImplTest {

    @Mock
    private UserEntityService userEntityService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private MyPageServiceImpl myPageService;

    private final Long userId = 1L;
    private UserEntity user;
    private MultipartFile mockFile;

    @BeforeEach
    void init() {
        mockFile = mock(MultipartFile.class);


        user = UserEntity.builder()
                .id(userId)
                .email("test@test.com")
                .username("testuser")
                .password("password")
                .name("홍길동")
                .nickname("길동")
                .phone("010-1234-5678")
                .address("서울")
                .profileImage("http://Profile.png")
                .build();
    }

    @Test
    void 마이페이지_정보_조회_정상_테스트() {
        // given
        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.of(user));

        // when
        UserProfileDTO dto = myPageService.getUserProfile(userId);

        // then
        assertNotNull(dto);
        assertEquals("test@test.com", dto.email());
        assertEquals("testuser", dto.username());
        assertEquals("홍길동", dto.name());
        assertEquals("길동", dto.nickname());
        assertEquals("010-1234-5678", dto.phone());
        assertEquals("서울", dto.address());
        assertEquals("http://Profile.png", dto.profileImage());
    }

    @Test
    void 마이페이지_정보_조회_유저없음_예외_테스트() {
        // given
        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.empty());

        // when
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            myPageService.getUserProfile(userId);
        });

        // then
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void 마이페이지_정보_수정_정상_테스트() {
        // given
        UpdateUserProfileDTO dto = new UpdateUserProfileDTO(
                "새로운닉네임",
                "010-1111-2222",
                "서울",
                "http://image.url/profile.png",
                null
        );
        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.of(user));

        // when
        myPageService.updateUserProfile(userId, dto);

        // then
        assertEquals(dto.nickname(), user.getNickname());
        assertEquals(dto.phone(), user.getPhone());
        assertEquals(dto.address(), user.getAddress());

        verify(userEntityService).save(user);
    }

    @Test
    void 마이페이지_정보_수정_이미지변경_정상_테스트() {
        // given
        given(mockFile.isEmpty()).willReturn(false);

        UpdateUserProfileDTO dto = new UpdateUserProfileDTO(
                "새로운닉네임",
                "010-1111-2222",
                "서울",
                "http://image.url/profile.png",
                mockFile
        );

        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.of(user));
        willDoNothing().given(userProfileService).deleteProfileImage(user.getProfileImage());
        given(userProfileService.uploadProfileAndReturnUrl(mockFile))
                .willReturn(Optional.of("http://NewProfile.png"));

        // when
        myPageService.updateUserProfile(userId, dto);

        // then
        verify(userProfileService).deleteProfileImage("http://Profile.png");
        verify(userProfileService).uploadProfileAndReturnUrl(mockFile);
        verify(userEntityService).save(user);

        assertEquals(dto.name(), user.getName());
        assertEquals("http://NewProfile.png", user.getProfileImage());
    }

    @Test
    void 마이페이지_정보_수정_유저없음_예외_테스트() {
        // given
        UpdateUserProfileDTO dto = new UpdateUserProfileDTO(
                "새로운닉네임",
                "010-1111-2222",
                "서울",
                "http://image.url/profile.png",
                null
        );

        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.empty());

        // when
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            myPageService.updateUserProfile(userId, dto);
        });

        // then
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void 마이페이지_비밀번호_변경_정상_테스트() {
        // given
        UpdatePasswordDTO dto = new UpdatePasswordDTO("password", "newPassword");
        String encodedNewPassword = "encodedNewPassword";
        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(dto.currentPassword(), user.getPassword())).willReturn(true);
        given(passwordEncoder.encode(dto.newPassword())).willReturn(encodedNewPassword);

        // when
        myPageService.updatePassword(1L, dto);

        // then
        assertEquals(encodedNewPassword, user.getPassword());
        verify(userEntityService).save(user);
        verify(passwordEncoder).encode(dto.newPassword());
    }

    @Test
    void 마이페이지_비밀번호_변경_비밀번호_불일치_예외_테스트() {
        // given
        UpdatePasswordDTO dto = new UpdatePasswordDTO("ppwd", "newPassword");
        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(dto.currentPassword(), user.getPassword())).willReturn(false);

        // when
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            myPageService.updatePassword(userId, dto);
        });

        // then
        assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
    }
}