package com.team03.ticketmon.queue.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.queue.service.AccessKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ✅ AccessKeyController
 * <p>
 * 사용자의 서비스 접근 키(Access Key)에 대한 만료 시간 연장 및 즉시 폐기 기능을 제공하는 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/access-keys")
@RequiredArgsConstructor
public class AccessKeyController {

    private final AccessKeyService accessKeyService;

    /**
     * <h1>[PATCH /api/access-keys/extend]</h1>
     * <p>
     * 특정 콘서트에 대한 사용자의 액세스 키 유효 시간을 연장
     * </p>
     * @param concertId 키를 연장할 콘서트의 ID
     * @param user      Spring Security가 주입해주는 인증된 사용자 정보
     * @return HTTP 200 OK와 함께 연장된 후의 새로운 유효 시간(초)을 반환
     */
    @PatchMapping("/extend")
    public ResponseEntity<SuccessResponse<Map<String, Long>>> extend(
            @RequestParam Long concertId,
            @AuthenticationPrincipal CustomUserDetails user) {

        long newTtl = accessKeyService.extendAccessKey(concertId, user.getUserId());
        return ResponseEntity.ok(SuccessResponse.of(
                "액세스 키 유효시간이 연장되었습니다.",
                Map.of("newTtlSeconds", newTtl)
        ));
    }

    /**
     * <h1>[DELETE /api/access-keys]</h1>
     * <p>
     * 특정 콘서트에 대한 사용자의 액세스 키를 즉시 만료(삭제)
     * </p>
     * @param concertId 키를 만료시킬 콘서트의 ID
     * @param user      Spring Security가 주입해주는 인증된 사용자 정보
     * @return HTTP 200 OK와 함께 성공 메시지를 반환
     */
    @DeleteMapping
    public ResponseEntity<SuccessResponse<Void>> expire(
            @RequestParam Long concertId,
            @AuthenticationPrincipal CustomUserDetails user) {

        accessKeyService.invalidateAccessKey(concertId, user.getUserId());
        return ResponseEntity.ok(SuccessResponse.of("액세스 키가 만료 처리되었습니다.", null));
    }
}