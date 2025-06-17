package com.team03.ticketmon.queue.controller;

import com.team03.ticketmon.queue.dto.EnterRequest;
import com.team03.ticketmon.queue.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
     * 사용자를 특정 콘서트의 대기열에 등록합니다.
     * 성공 시, 사용자의 현재 대기 순번을 포함한 상태 정보를 반환합니다.
     *
     * @param request 클라이언트로부터 받은 대기열 진입 요청 데이터 (concertId, userId)
     * @return HTTP 200 OK와 함께 대기열 상태 정보(userId, rank, status)를 담은 응답
     */
    @PostMapping("/enter")
    public ResponseEntity<Map<String, Object>> enterQueue(@RequestBody EnterRequest request) {
        // TODO: [보안] 현재는 요청 본문(request body)에서 userId를 직접 받지만, 이는 매우 위험합니다.
        // 실제 운영 환경에서는 반드시 Spring Security의 Authentication 객체(예: @AuthenticationPrincipal)에서
        // 인증된 사용자 정보를 가져와 사용해야 합니다.
        String userId = request.userId();
        Long concertId = request.concertId();

        // 어떤 요청이 들어왔는지 INFO 레벨로 기록합니다. API 트래픽 모니터링에 필수적입니다.
        log.info("대기열 진입 요청 수신. 사용자 ID: {}, 콘서트 ID: {}", userId, concertId);

        // 핵심 비즈니스 로직 호출
        Long rank = waitingQueueService.apply(concertId, userId);

        // TODO: [개선점] 응답 형식이 복잡해질 경우, Map 대신 전용 응답 DTO(예: EnterResponse.java)를
        // 생성하여 사용하는 것이 타입 안정성과 코드 가독성 면에서 더 좋습니다.
        Map<String, Object> response = Map.of(
                "userId", userId,
                "rank", rank,
                "status", "WAITING"
        );

        // 개발 환경에서 어떤 응답을 반환하는지 상세히 확인하기 위한 DEBUG 레벨 로그.
        // 운영 배포 시에는 로그 레벨을 INFO로 조정하여 이 로그가 출력되지 않도록 해야 합니다.
        log.debug("대기열 진입 응답: {}", response);

        return ResponseEntity.ok(response);
    }
}