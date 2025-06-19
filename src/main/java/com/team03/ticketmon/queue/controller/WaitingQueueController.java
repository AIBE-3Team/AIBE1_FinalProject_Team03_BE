package com.team03.ticketmon.queue.controller;

import com.team03.ticketmon.queue.dto.EnterResponse;
import com.team03.ticketmon.queue.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.team03.ticketmon.auth.jwt.CustomUserDetails; // CustomUserDetails 임포트 필요

/**
 * 대기열 시스템의 API 엔드포인트를 제공하는 컨트롤러입니다.
 * 클라이언트는 이 컨트롤러를 통해 대기열에 진입을 요청할 수 있습니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;

    /**
     * [POST /api/queue/enter]
     * 사용자를 특정 콘서트의 대기열에 등록
     *
     * @param concertId 클라이언트로부터 받은 콘서트 ID
     * @param userDetails Spring Security가 JWT 토큰을 바탕으로 주입해주는 인증된 사용자 정보
     * @return HTTP 200 OK와 함께 대기열 상태 정보
     */
    @PostMapping("/enter")
    public ResponseEntity<EnterResponse> enterQueue(
            @RequestParam Long concertId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // 1. JWT 토큰에서 직접 사용자 정보를 가져와 안전하게 사용
        String userId = userDetails.getUserId().toString();

        // 2. 어떤 요청이 들어왔는지 INFO 레벨로 기록
        log.info("대기열 진입 요청 수신. [인증된 사용자] ID: {}, 콘서트 ID: {}", userId, concertId);

        // 3. 핵심 비즈니스 로직 호출
        Long rank = waitingQueueService.apply(concertId, userId);

        EnterResponse enterResponse = new EnterResponse(userId, rank, "WAITING");
        log.debug("대기열 진입 응답: {}", enterResponse);

        return ResponseEntity.ok(enterResponse);
    }
}