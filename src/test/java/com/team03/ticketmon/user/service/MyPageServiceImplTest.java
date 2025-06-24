package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.dto.UpdateUserProfileDTO;
import com.team03.ticketmon.user.dto.UserProfileDTO;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("마이페이지 테스트")
class MyPageServiceImplTest {

    @Mock
    private UserEntityService userEntityService;

    @InjectMocks
    private MyPageServiceImpl myPageService;

    private final Long userId = 1L;
    private UserEntity user;
    @BeforeEach
    void init() {
        user = UserEntity.builder()
                .id(userId)
                .email("test@test.com")
                .username("testuser")
                .name("홍길동")
                .nickname("길동")
                .phone("010-1234-5678")
                .address("서울")
                .profileImage("http://image.url/profile.png")
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
        assertEquals("http://image.url/profile.png", dto.profileImage());
    }

    @Test
    void 마이페이지_정보_조회_유저없음_예외_테스트() {
        // given
        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.empty());

        // when
        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> {
            myPageService.getUserProfile(userId);
        });

        // then
        assertEquals("회원 정보가 없습니다.", ex.getMessage());
    }

    @Test
    void 마이페이지_정보_수정_정상_테스트() {
        // given
        UpdateUserProfileDTO dto = new UpdateUserProfileDTO(
                "test@test.com",
                "testuser",
                "홍길동",
                "새로운닉네임",
                "010-1111-2222",
                "서울",
                "http://image.url/profile.png"
        );
        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.of(user));

        // when
        myPageService.updateUserProfile(userId, dto);

        // then
        assertEquals(dto.email(), user.getEmail());
        assertEquals(dto.username(), user.getUsername());
        assertEquals(dto.name(), user.getName());
        assertEquals(dto.nickname(), user.getNickname());
        assertEquals(dto.phone(), user.getPhone());
        assertEquals(dto.address(), user.getAddress());
        assertEquals(dto.profileImage(), user.getProfileImage());

        verify(userEntityService).save(user);
    }
    
    @Test
    void 마이페이지_정보_수정_유저없음_예외_테스트() {
        // given
        UpdateUserProfileDTO dto = new UpdateUserProfileDTO(
                "test@test.com",
                "testuser",
                "홍길동",
                "새로운닉네임",
                "010-1111-2222",
                "서울",
                "http://image.url/profile.png"
        );

        given(userEntityService.findUserEntityByUserId(userId)).willReturn(Optional.empty());

        // when
        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> {
            myPageService.updateUserProfile(userId, dto);
        });

        // then
        assertEquals("회원 정보가 없습니다.", ex.getMessage());
    }
}