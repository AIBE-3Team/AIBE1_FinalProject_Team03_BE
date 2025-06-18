// src/main/java/com/team03/ticketmon/seat/controller/SeatReservationController.java
package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.dto.SeatReserveRequest;
import com.team03.ticketmon.seat.dto.SeatStatusResponse;
import com.team03.ticketmon.seat.exception.SeatReservationException;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 좌석 예약 관리 컨트롤러
 * - 좌석 임시 선점 (분산 락 적용)
 * - 좌석 선점 해제 (권한 검증)
 * - 실시간 동시성 제어
 */
@Tag(name = "좌석 예약 관리", description = "좌석 선점/해제 API (분산 락 적용)")
@Slf4j
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatReservationController {

    private final SeatStatusService seatStatusService;
    private final RedissonClient redissonClient;

    // 분산 락 설정
    private static final String SEAT_CONTROLLER_LOCK_PREFIX = "seat:controller:lock:";
    private static final long LOCK_WAIT_TIME = 5; // 락 획득 대기 시간 (초)
    private static final long LOCK_LEASE_TIME = 10; // 락 보유 시간 (초)

    // 좌석 배치 설정
    private static final int SEATS_PER_SECTION = 50;
    private static final int MAX_SEATS = 150; // 3개 섹션 * 50석

    @Operation(summary = "좌석 임시 선점", description = "좌석을 5분간 임시 선점합니다 (분산 락 적용)")
    @PostMapping("/concerts/{concertId}/seats/{seatId}/reserve")
    public ResponseEntity<SuccessResponse<SeatStatusResponse>> reserveSeat(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId,
            @RequestBody SeatReserveRequest request) {

        // 컨트롤러 레벨에서 분산 락 적용
        String lockKey = SEAT_CONTROLLER_LOCK_PREFIX + concertId + ":" + seatId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 분산 락 획득 시도
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("좌석 선점 락 획득 실패 (컨트롤러): concertId={}, seatId={}, userId={}",
                        concertId, seatId, request.userId());
                return ResponseEntity.status(423) // 423 Locked
                        .body(SuccessResponse.of("다른 사용자가 처리 중입니다. 잠시 후 다시 시도해주세요.", null));
            }

            log.debug("좌석 선점 락 획득 성공 (컨트롤러): concertId={}, seatId={}, userId={}",
                    concertId, seatId, request.userId());

            // === 임계 구역 시작 ===

            // 1. 현재 좌석 상태 확인
            Optional<SeatStatus> currentStatus = seatStatusService.getSeatStatus(concertId, seatId);

            if (currentStatus.isPresent() && !currentStatus.get().isAvailable()) {
                SeatStatus seat = currentStatus.get();

                if (seat.isExpired()) {
                    // 만료된 선점이면 해제 후 다시 선점
                    log.info("만료된 선점 좌석 해제 후 재선점: concertId={}, seatId={}", concertId, seatId);
                    seatStatusService.forceReleaseSeat(concertId, seatId);
                } else if (seat.isReserved()) {
                    // 동일 사용자의 재요청 확인
                    if (request.userId().equals(seat.getUserId())) {
                        log.info("동일 사용자 재선점 요청: concertId={}, seatId={}, userId={}",
                                concertId, seatId, request.userId());
                        SeatStatusResponse response = SeatStatusResponse.from(seat);
                        return ResponseEntity.ok(SuccessResponse.of("이미 선점한 좌석입니다", response));
                    } else {
                        return ResponseEntity.badRequest()
                                .body(SuccessResponse.of("다른 사용자가 선점 중인 좌석입니다", null));
                    }
                } else {
                    // BOOKED 또는 UNAVAILABLE 상태
                    return ResponseEntity.badRequest()
                            .body(SuccessResponse.of("예매할 수 없는 좌석입니다. 상태: " + seat.getStatus(), null));
                }
            }

            // 2. 좌석 정보 생성 (실제로는 DB에서 조회)
            String seatInfo = generateSeatInfo(seatId.intValue());

            // 3. 좌석 선점 처리 (서비스 레이어의 원자적 처리 활용)
            SeatStatus reservedSeat = seatStatusService.reserveSeat(
                    concertId, seatId, request.userId(), seatInfo);

            SeatStatusResponse response = SeatStatusResponse.from(reservedSeat);

            log.info("좌석 선점 완료 (컨트롤러): concertId={}, seatId={}, userId={}",
                    concertId, seatId, request.userId());

            return ResponseEntity.ok(SuccessResponse.of("좌석 선점 성공", response));

            // === 임계 구역 종료 ===

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("좌석 선점 처리 중 인터럽트 (컨트롤러): concertId={}, seatId={}, userId={}",
                    concertId, seatId, request.userId(), e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 선점 처리가 중단되었습니다.", null));
        } catch (SeatReservationException e) {
            log.warn("좌석 선점 실패 (비즈니스 로직): concertId={}, seatId={}, userId={}, message={}",
                    concertId, seatId, request.userId(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SuccessResponse.of(e.getMessage(), null));
        } catch (Exception e) {
            log.error("좌석 선점 처리 중 예기치 않은 오류 (컨트롤러): concertId={}, seatId={}, userId={}",
                    concertId, seatId, request.userId(), e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 선점 처리 중 오류가 발생했습니다.", null));
        } finally {
            // 락 해제 (안전한 해제)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("좌석 선점 락 해제 완료 (컨트롤러): concertId={}, seatId={}, userId={}",
                        concertId, seatId, request.userId());
            }
        }
    }

    @Operation(summary = "좌석 선점 해제", description = "임시 선점된 좌석을 해제합니다 (권한 검증 포함)")
    @PostMapping("/concerts/{concertId}/seats/{seatId}/release")
    public ResponseEntity<SuccessResponse<String>> releaseSeat(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId) {

        try {
            // 현재 인증된 사용자 정보 획득
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("인증되지 않은 사용자의 좌석 해제 시도: concertId={}, seatId={}", concertId, seatId);
                return ResponseEntity.status(401)
                        .body(SuccessResponse.of("인증이 필요합니다.", null));
            }

            // 사용자 ID 추출 (실제 구현에서는 JWT 토큰에서 추출)
            Long currentUserId = extractUserIdFromAuthentication(authentication);
            if (currentUserId == null) {
                log.warn("사용자 ID를 확인할 수 없는 좌석 해제 시도: concertId={}, seatId={}", concertId, seatId);
                return ResponseEntity.status(400)
                        .body(SuccessResponse.of("사용자 정보를 확인할 수 없습니다.", null));
            }

            // 권한 검증을 포함한 좌석 해제
            seatStatusService.releaseSeat(concertId, seatId, currentUserId);

            log.info("좌석 선점 해제 성공: concertId={}, seatId={}, userId={}", concertId, seatId, currentUserId);
            return ResponseEntity.ok(SuccessResponse.of("좌석 선점 해제 성공", "SUCCESS"));

        } catch (SeatReservationException e) {
            log.warn("좌석 해제 실패: concertId={}, seatId={}, message={}",
                    concertId, seatId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SuccessResponse.of(e.getMessage(), null));
        } catch (Exception e) {
            log.error("좌석 해제 처리 중 오류: concertId={}, seatId={}", concertId, seatId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 해제 처리 중 오류가 발생했습니다.", null));
        }
    }

    /**
     * 좌석 정보 문자열 생성 (더미 데이터용, 검증 로직 포함)
     * 실제 운영에서는 DB에서 조회해야 함
     *
     * @param seatNumber 좌석 번호 (1부터 시작)
     * @return 좌석 정보 문자열 (예: A-1, B-25, C-10)
     * @throws IllegalArgumentException 좌석 번호가 유효 범위를 벗어난 경우
     */
    private String generateSeatInfo(int seatNumber) {
        // 좌석 번호 유효성 검증
        if (seatNumber < 1 || seatNumber > MAX_SEATS) {
            log.error("유효하지 않은 좌석 번호: seatNumber={}, maxSeats={}", seatNumber, MAX_SEATS);
            throw new IllegalArgumentException(
                    String.format("좌석 번호는 1부터 %d 사이여야 합니다. 입력된 값: %d", MAX_SEATS, seatNumber));
        }

        String section;
        int seatInSection;

        if (seatNumber <= SEATS_PER_SECTION) {
            // A구역: 1-50
            section = "A";
            seatInSection = seatNumber;
        } else if (seatNumber <= SEATS_PER_SECTION * 2) {
            // B구역: 51-100
            section = "B";
            seatInSection = seatNumber - SEATS_PER_SECTION;
        } else if (seatNumber <= SEATS_PER_SECTION * 3) {
            // C구역: 101-150
            section = "C";
            seatInSection = seatNumber - (SEATS_PER_SECTION * 2);
        } else {
            // 이론적으로 도달할 수 없는 경우 (위에서 검증했지만 방어적 프로그래밍)
            log.error("예상치 못한 좌석 번호 범위: seatNumber={}", seatNumber);
            throw new IllegalArgumentException("좌석 번호 처리 중 내부 오류가 발생했습니다.");
        }

        String seatInfo = section + "-" + seatInSection;
        log.debug("좌석 정보 생성: seatNumber={} -> seatInfo={}", seatNumber, seatInfo);

        return seatInfo;
    }

    /**
     * ✅ 개선된 사용자 ID 추출 메서드 (테스트용 더미 구현)
     * 실제 구현에서는 JWT 토큰 또는 세션에서 사용자 정보 추출
     *
     * @param authentication Spring Security 인증 정보
     * @return 사용자 ID (추출 실패 시 테스트용 기본값 1L 반환)
     */
    private Long extractUserIdFromAuthentication(Authentication authentication) {
        try {
            // TODO: 실제 구현에서는 JWT 토큰에서 사용자 ID 추출
            // 예시: JWT Claims에서 "userId" 클레임 추출
            // JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
            // return jwtToken.getToken().getClaimAsLong("userId");

            // ✅ 테스트용 더미 구현 개선
            String principal = authentication.getName();
            log.debug("Authentication principal: {}", principal);

            // 기본값으로 사용자 ID 1 반환 (테스트용)
            if ("anonymousUser".equals(principal)) {
                log.debug("익명 사용자 - 테스트용 사용자 ID 1 반환");
                return 1L; // 테스트용 기본 사용자 ID
            }

            // principal이 숫자인 경우 파싱
            if (principal != null && principal.matches("\\d+")) {
                return Long.parseLong(principal);
            }

            // 그 외의 경우 테스트용 기본값
            log.debug("기본 테스트 사용자 ID 1 반환");
            return 1L;

        } catch (Exception e) {
            log.error("사용자 ID 추출 중 오류 발생", e);
            return 1L; // 테스트용 기본값
        }
    }
}