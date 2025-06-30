package com.team03.ticketmon.queue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon._global.config.SecurityConfig;
import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import com.team03.ticketmon.auth.service.ReissueService;
import com.team03.ticketmon.queue.dto.EnterResponse;
import com.team03.ticketmon.queue.service.WaitingQueueService;
import com.team03.ticketmon.support.WithMockCustomUser;
import com.team03.ticketmon.user.service.SocialUserService;
import com.team03.ticketmon.user.service.UserEntityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WaitingQueueController.class) // WaitingQueueController를 테스트 대상으로 지정
@Import(SecurityConfig.class) // Security 설정을 가져와 필터 체인을 활성화
class WaitingQueueControllerTest {

    @Autowired private MockMvc mockMvc; // HTTP 요청을 모의로 실행하는 객체
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private WaitingQueueService waitingQueueService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private ReissueService reissueService;
    @MockitoBean private RefreshTokenService refreshTokenService;
    @MockitoBean private UserEntityService userEntityService;
    @MockitoBean private SocialUserService socialUserService;
    @MockitoBean private CookieUtil cookieUtil;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;


    @Test
    @DisplayName("성공: 인증된 사용자가 대기열 진입 요청시 200 OK와 순위를 반환한다")
    @WithMockCustomUser(userId = 123L) // userId=123L인 사용자가 로그인했다고 가정
    void givenAuthenticatedUser_whenEnterQueue_thenReturnsOkWithRank() throws Exception {
        // GIVEN: 서비스 계층의 동작을 미리 정의
        long concertId = 1L;
        long expectedRank = 100L;
        long expectedUserId = 123L;

        // waitingQueueService.apply(1L, "123")이 호출되면, 100L을 반환하도록 설정
        given(waitingQueueService.apply(concertId, expectedUserId)).willReturn(EnterResponse.waiting(expectedRank));

        // WHEN: 실제 HTTP 요청을 시뮬레이션
        mockMvc.perform(post("/api/queue/enter")
                .param("concertId", String.valueOf(concertId)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rank").value(expectedRank))
            .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    @DisplayName("실패: 인증되지 않은 사용자가 요청시 401 Unauthorized를 반환한다")
    void givenUnauthenticatedUser_whenEnterQueue_thenReturnsUnauthorized() throws Exception {
        // GIVEN: 인증 정보 없음

        // WHEN & THEN: HTTP 요청 시 401 에러가 발생하는지 검증
        mockMvc.perform(post("/api/queue/enter")
                .param("concertId", "1"))
            .andDo(print())
            .andExpect(status().isUnauthorized()); // SecurityConfig에 의해 401 응답
    }

    @Test
    @DisplayName("실패: concertId 파라미터가 누락되면 400 Bad Request를 반환한다")
    @WithMockCustomUser // 인증된 사용자
    void givenMissingParameter_whenEnterQueue_thenReturnsBadRequest() throws Exception {
        // GIVEN: concertId 파라미터가 없는 요청

        // WHEN & THEN: HTTP 요청 시 400 에러가 발생하는지 검증
        mockMvc.perform(post("/api/queue/enter"))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }
}