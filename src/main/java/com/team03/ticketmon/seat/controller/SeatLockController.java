// src/main/java/com/team03/ticketmon/seat/controller/SeatLockController.java
package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.seat.dto.BulkSeatLockResult;
import com.team03.ticketmon.seat.dto.SeatLockResult;
import com.team03.ticketmon.seat.service.SeatLockService;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 좌석 영구 선점 관리 컨트롤러 (확장된 버전)
 *
 * 목적: TTL 삭제 후 좌석을 영구적으로 선점 상태로 변경하는 API 제공
 *
 * 주요 기능:
 * - 단일 좌석 영구 선점 처리 (기존 기능)
 * - 다중 좌석 일괄 영구 선점 처리 (신규 기능)
 * - 단일 좌석 선점 상태 복원 (기존 기능)
 * - 다중 좌석 일괄 상태 복원 (신규 기능)
 * - 영구 선점 가능 여부 확인
 *
 * 보안: 인증된 사용자만 접근 가능, 본인이 선점한 좌석만 처리
 */
@Tag(name = "좌석 영구 선점 관리", description = "TTL 삭제 및 영구 선점 상태 변경 API (단일/다중 좌석 지원)")
@Slf4j
@RestController
@RequestMapping("/api/seats/lock")
@RequiredArgsConstructor
public class SeatLockController {

    private final SeatLockService seatLockService;
    private final SeatStatusService seatStatusService;

    // ========== 기존 단일 좌석 처리 API들 ==========

    /**
     * 좌석 영구 선점 처리
     *
     * 현재 임시 선점된 좌석을 영구적으로 선점 상태로 변경합니다.
     * TTL 키를 삭제하여 자동 만료를 방지하고, 결제 처리 중 좌석이 해제되지 않도록 보장합니다.
     *
     * 사용 시나리오:
     * - 사용자가 결제 버튼을 누른 순간
     * - 토스페이먼츠 결제 페이지로 이동하기 직전
     * - 결제 처리가 진행되는 동안 좌석 안전성 보장
     */
    @Operation(
            summary = "좌석 영구 선점",
            description = "임시 선점된 좌석을 영구 선점 상태로 변경하고 TTL을 삭제합니다."
    )
    @PostMapping("/concerts/{concertId}/seats/{seatId}/permanent")
    public ResponseEntity<SuccessResponse<SeatLockResult>> lockSeatPermanently(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId,
            @AuthenticationPrincipal CustomUserDetails user) {

        log.info("좌석 영구 선점 요청: concertId={}, seatId={}, userId={}",
                concertId, seatId, user.getUserId());

        try {
            // 영구 선점 처리
            SeatLockResult result = seatLockService.lockSeatPermanently(
                    concertId, seatId, user.getUserId());

            if (result.isSuccess()) {
                log.info("좌석 영구 선점 성공: {}", result.getSummary());

                return ResponseEntity.ok(
                        SuccessResponse.of(
                                "좌석이 영구 선점되었습니다. 결제를 진행해주세요.",
                                result
                        )
                );
            } else {
                log.warn("좌석 영구 선점 실패: {}", result.getSummary());

                return ResponseEntity.badRequest().body(
                        SuccessResponse.of(
                                result.getErrorMessage(),
                                result
                        )
                );
            }

        } catch (Exception e) {
            log.error("좌석 영구 선점 중 예외 발생: concertId={}, seatId={}, userId={}",
                    concertId, seatId, user.getUserId(), e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "좌석 영구 선점 처리 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    /**
     * 좌석 선점 상태 복원
     *
     * 영구 선점된 좌석을 다시 일반 선점 상태로 되돌립니다.
     * 결제 실패나 사용자 취소 시 좌석을 원래 상태로 복원합니다.
     *
     * 사용 시나리오:
     * - 토스페이먼츠 결제 실패 시
     * - 사용자가 결제를 취소한 경우
     * - 결제 타임아웃 발생 시
     */
    @Operation(
            summary = "좌석 선점 상태 복원",
            description = "영구 선점된 좌석을 일반 선점 상태로 되돌리고 선택적으로 TTL을 재설정합니다."
    )
    @PostMapping("/concerts/{concertId}/seats/{seatId}/restore")
    public ResponseEntity<SuccessResponse<SeatLockResult>> restoreSeatReservation(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId,
            @Parameter(description = "TTL 재설정 여부", example = "true")
            @RequestParam(defaultValue = "true") boolean restoreWithTTL,
            @AuthenticationPrincipal CustomUserDetails user) {

        log.info("좌석 선점 상태 복원 요청: concertId={}, seatId={}, userId={}, withTTL={}",
                concertId, seatId, user.getUserId(), restoreWithTTL);

        try {
            // 선점 상태 복원
            SeatLockResult result = seatLockService.restoreSeatReservation(
                    concertId, seatId, user.getUserId(), restoreWithTTL);

            if (result.isSuccess()) {
                log.info("좌석 선점 상태 복원 성공: {}", result.getSummary());

                String message = restoreWithTTL ?
                        "좌석 선점 상태가 복원되었습니다. 5분 내 다시 결제해주세요." :
                        "좌석 선점 상태가 복원되었습니다.";

                return ResponseEntity.ok(
                        SuccessResponse.of(message, result)
                );
            } else {
                log.warn("좌석 선점 상태 복원 실패: {}", result.getSummary());

                return ResponseEntity.badRequest().body(
                        SuccessResponse.of(
                                result.getErrorMessage(),
                                result
                        )
                );
            }

        } catch (Exception e) {
            log.error("좌석 선점 상태 복원 중 예외 발생: concertId={}, seatId={}, userId={}",
                    concertId, seatId, user.getUserId(), e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "좌석 선점 상태 복원 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    // ========== 신규 다중 좌석 처리 API들 ==========

    /**
     * 사용자의 모든 선점 좌석 일괄 영구 선점 처리
     *
     * 사용자가 현재 선점하고 있는 모든 좌석을 한 번에 영구 선점 상태로 변경합니다.
     * 다중 좌석 선택 후 결제 진행 시 모든 좌석의 안전성을 보장합니다.
     *
     * 사용 시나리오:
     * - 여러 좌석을 선택한 사용자가 결제 버튼을 누른 순간
     * - 토스페이먼츠 결제 페이지로 이동하기 직전
     * - 모든 선점 좌석의 동시 영구 선점이 필요한 경우
     */
    @Operation(
            summary = "사용자 모든 좌석 일괄 영구 선점",
            description = "사용자가 선점한 모든 좌석을 한 번에 영구 선점 상태로 변경합니다."
    )
    @PostMapping("/concerts/{concertId}/users/permanent-all")
    public ResponseEntity<SuccessResponse<BulkSeatLockResult>> lockAllUserSeatsPermanently(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @AuthenticationPrincipal CustomUserDetails user) {

        log.info("사용자 모든 좌석 일괄 영구 선점 요청: concertId={}, userId={}",
                concertId, user.getUserId());

        try {
            // 일괄 영구 선점 처리
            BulkSeatLockResult result = seatLockService.lockAllUserSeatsPermanently(
                    concertId, user.getUserId());

            if (result.isPartialSuccess()) {
                String message;

                if (result.isAllSuccess()) {
                    message = String.format("모든 좌석이 영구 선점되었습니다. (%d석)", result.getSuccessCount());
                    log.info("사용자 모든 좌석 일괄 영구 선점 완전 성공: {}", result.getSummary());
                } else {
                    message = String.format("일부 좌석이 영구 선점되었습니다. (성공: %d석, 실패: %d석)",
                            result.getSuccessCount(), result.getFailureCount());
                    log.warn("사용자 모든 좌석 일괄 영구 선점 부분 성공: {}", result.getSummary());
                }

                return ResponseEntity.ok(
                        SuccessResponse.of(message, result)
                );
            } else {
                log.warn("사용자 모든 좌석 일괄 영구 선점 실패: {}", result.getSummary());

                return ResponseEntity.badRequest().body(
                        SuccessResponse.of(
                                result.getErrorMessage() != null ?
                                        result.getErrorMessage() : "모든 좌석의 영구 선점에 실패했습니다.",
                                result
                        )
                );
            }

        } catch (Exception e) {
            log.error("사용자 모든 좌석 일괄 영구 선점 중 예외 발생: concertId={}, userId={}",
                    concertId, user.getUserId(), e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "일괄 영구 선점 처리 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    /**
     * 사용자의 모든 영구 선점 좌석 일괄 상태 복원
     *
     * 사용자가 현재 영구 선점하고 있는 모든 좌석을 한 번에 일반 선점 상태로 복원합니다.
     * 결제 실패나 취소 시 모든 좌석을 원래 상태로 되돌릴 때 사용합니다.
     *
     * 사용 시나리오:
     * - 토스페이먼츠 결제 실패 시
     * - 사용자가 결제를 취소한 경우
     * - 결제 타임아웃 발생 시
     * - 모든 영구 선점 좌석의 동시 복원이 필요한 경우
     */
    @Operation(
            summary = "사용자 모든 좌석 일괄 상태 복원",
            description = "사용자가 영구 선점한 모든 좌석을 한 번에 일반 선점 상태로 복원합니다."
    )
    @PostMapping("/concerts/{concertId}/users/restore-all")
    public ResponseEntity<SuccessResponse<BulkSeatLockResult>> restoreAllUserSeats(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "TTL 재설정 여부", example = "true")
            @RequestParam(defaultValue = "true") boolean restoreWithTTL,
            @AuthenticationPrincipal CustomUserDetails user) {

        log.info("사용자 모든 좌석 일괄 상태 복원 요청: concertId={}, userId={}, withTTL={}",
                concertId, user.getUserId(), restoreWithTTL);

        try {
            // 일괄 상태 복원 처리
            BulkSeatLockResult result = seatLockService.restoreAllUserSeats(
                    concertId, user.getUserId(), restoreWithTTL);

            if (result.isPartialSuccess()) {
                String message;

                if (result.isAllSuccess()) {
                    message = restoreWithTTL ?
                            String.format("모든 좌석의 선점 상태가 복원되었습니다. 5분 내 다시 결제해주세요. (%d석)",
                                    result.getSuccessCount()) :
                            String.format("모든 좌석의 선점 상태가 복원되었습니다. (%d석)",
                                    result.getSuccessCount());
                    log.info("사용자 모든 좌석 일괄 상태 복원 완전 성공: {}", result.getSummary());
                } else {
                    message = String.format("일부 좌석의 상태가 복원되었습니다. (성공: %d석, 실패: %d석)",
                            result.getSuccessCount(), result.getFailureCount());
                    log.warn("사용자 모든 좌석 일괄 상태 복원 부분 성공: {}", result.getSummary());
                }

                return ResponseEntity.ok(
                        SuccessResponse.of(message, result)
                );
            } else {
                log.warn("사용자 모든 좌석 일괄 상태 복원 실패: {}", result.getSummary());

                return ResponseEntity.badRequest().body(
                        SuccessResponse.of(
                                result.getErrorMessage() != null ?
                                        result.getErrorMessage() : "모든 좌석의 상태 복원에 실패했습니다.",
                                result
                        )
                );
            }

        } catch (Exception e) {
            log.error("사용자 모든 좌석 일괄 상태 복원 중 예외 발생: concertId={}, userId={}",
                    concertId, user.getUserId(), e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "일괄 상태 복원 처리 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    // ========== 기존 조회/확인 API들 ==========

    /**
     * 좌석 영구 선점 가능 여부 확인
     *
     * 실제 영구 선점 처리 없이 가능 여부만 미리 확인합니다.
     * 프론트엔드에서 "결제하기" 버튼 활성화 여부 결정 시 사용할 수 있습니다.
     */
    @Operation(
            summary = "좌석 영구 선점 가능 여부 확인",
            description = "실제 처리 없이 좌석 영구 선점이 가능한지 확인합니다."
    )
    @GetMapping("/concerts/{concertId}/seats/{seatId}/check")
    public ResponseEntity<SuccessResponse<SeatLockService.SeatLockCheckResult>> checkSeatLockEligibility(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId,
            @AuthenticationPrincipal CustomUserDetails user) {

        log.debug("좌석 영구 선점 가능 여부 확인: concertId={}, seatId={}, userId={}",
                concertId, seatId, user.getUserId());

        try {
            // 영구 선점 가능 여부 확인
            SeatLockService.SeatLockCheckResult checkResult =
                    seatLockService.checkSeatLockEligibility(concertId, seatId, user.getUserId());

            if (checkResult.isEligible()) {
                return ResponseEntity.ok(
                        SuccessResponse.of(
                                String.format("영구 선점 가능 (잔여 TTL: %d초)", checkResult.getRemainingTTL()),
                                checkResult
                        )
                );
            } else {
                return ResponseEntity.badRequest().body(
                        SuccessResponse.of(
                                checkResult.getMessage(),
                                checkResult
                        )
                );
            }

        } catch (Exception e) {
            log.error("좌석 영구 선점 가능 여부 확인 중 예외 발생: concertId={}, seatId={}, userId={}",
                    concertId, seatId, user.getUserId(), e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "좌석 영구 선점 가능 여부 확인 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    /**
     * 사용자의 영구 선점 좌석 목록 조회
     *
     * 현재 사용자가 영구 선점한 모든 좌석 목록을 조회합니다.
     * 결제 진행 중인 좌석들을 확인할 때 사용합니다.
     */
    @Operation(
            summary = "사용자 영구 선점 좌석 목록",
            description = "현재 사용자가 영구 선점한 좌석 목록을 조회합니다."
    )
    @GetMapping("/concerts/{concertId}/my-locks")
    public ResponseEntity<SuccessResponse<UserLockedSeatsResponse>> getUserLockedSeats(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @AuthenticationPrincipal CustomUserDetails user) {

        log.debug("사용자 영구 선점 좌석 조회: concertId={}, userId={}",
                concertId, user.getUserId());

        try {
            // 사용자의 모든 선점 좌석 조회 (임시 + 영구 선점 포함)
            List<com.team03.ticketmon.seat.domain.SeatStatus> allReservedSeats =
                    seatStatusService.getUserReservedSeats(concertId, user.getUserId());

            // 영구 선점된 좌석만 필터링 (expiresAt이 null인 RESERVED 상태)
            List<SeatLockInfo> permanentlyLockedSeats = allReservedSeats.stream()
                    .filter(seat -> seat.isReserved() && seat.getExpiresAt() == null)
                    .map(seat -> SeatLockInfo.builder()
                            .seatId(seat.getSeatId())
                            .seatInfo(seat.getSeatInfo())
                            .lockedAt(seat.getReservedAt())
                            .status("PERMANENTLY_LOCKED")
                            .build())
                    .collect(java.util.stream.Collectors.toList());

            UserLockedSeatsResponse response = UserLockedSeatsResponse.builder()
                    .concertId(concertId)
                    .userId(user.getUserId())
                    .lockedSeats(permanentlyLockedSeats)
                    .totalCount(permanentlyLockedSeats.size())
                    .message(permanentlyLockedSeats.isEmpty() ?
                            "영구 선점된 좌석이 없습니다." :
                            String.format("%d개의 좌석이 영구 선점되어 있습니다.", permanentlyLockedSeats.size()))
                    .build();

            return ResponseEntity.ok(
                    SuccessResponse.of(
                            "영구 선점 좌석 목록 조회 완료",
                            response
                    )
            );

        } catch (Exception e) {
            log.error("사용자 영구 선점 좌석 조회 중 예외 발생: concertId={}, userId={}",
                    concertId, user.getUserId(), e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "영구 선점 좌석 목록 조회 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    /**
     * 사용자가 일괄 처리 가능한 좌석 수 조회
     *
     * 현재 사용자가 영구 선점 또는 상태 복원할 수 있는 좌석 수를 미리 확인합니다.
     * 프론트엔드에서 "모든 좌석 영구 선점" 버튼 표시 여부 결정 시 사용할 수 있습니다.
     */
    @Operation(
            summary = "일괄 처리 가능한 좌석 수 조회",
            description = "사용자가 영구 선점하거나 복원할 수 있는 좌석 수를 확인합니다."
    )
    @GetMapping("/concerts/{concertId}/bulk-eligible")
    public ResponseEntity<SuccessResponse<BulkEligibilityResponse>> getBulkEligibility(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @AuthenticationPrincipal CustomUserDetails user) {

        log.debug("일괄 처리 가능 좌석 수 조회: concertId={}, userId={}",
                concertId, user.getUserId());

        try {
            // 사용자의 모든 선점 좌석 조회
            List<com.team03.ticketmon.seat.domain.SeatStatus> allReservedSeats =
                    seatStatusService.getUserReservedSeats(concertId, user.getUserId());

            // 영구 선점 가능한 좌석 수 (임시 선점 상태)
            long lockEligibleCount = allReservedSeats.stream()
                    .filter(seat -> seat.isReserved() && seat.getExpiresAt() != null && !seat.isExpired())
                    .count();

            // 복원 가능한 좌석 수 (영구 선점 상태)
            long restoreEligibleCount = allReservedSeats.stream()
                    .filter(seat -> seat.isReserved() && seat.getExpiresAt() == null)
                    .count();

            BulkEligibilityResponse response = BulkEligibilityResponse.builder()
                    .concertId(concertId)
                    .userId(user.getUserId())
                    .totalReservedSeats(allReservedSeats.size())
                    .lockEligibleSeats((int) lockEligibleCount)
                    .restoreEligibleSeats((int) restoreEligibleCount)
                    .canBulkLock(lockEligibleCount > 0)
                    .canBulkRestore(restoreEligibleCount > 0)
                    .build();

            return ResponseEntity.ok(
                    SuccessResponse.of(
                            "일괄 처리 가능 좌석 수 조회 완료",
                            response
                    )
            );

        } catch (Exception e) {
            log.error("일괄 처리 가능 좌석 수 조회 중 예외 발생: concertId={}, userId={}",
                    concertId, user.getUserId(), e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "일괄 처리 가능 좌석 수 조회 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    // ========== 응답 DTO 클래스들 ==========

    /**
     * 사용자 영구 선점 좌석 응답 DTO (내부 클래스)
     */
    @lombok.Builder
    @lombok.Getter
    public static class UserLockedSeatsResponse {
        private final Long concertId;
        private final Long userId;
        private final List<SeatLockInfo> lockedSeats;
        private final int totalCount;
        private final String message;
    }

    /**
     * 좌석 선점 정보 DTO (내부 클래스)
     */
    @lombok.Builder
    @lombok.Getter
    public static class SeatLockInfo {
        private final Long seatId;
        private final String seatInfo;
        private final java.time.LocalDateTime lockedAt;
        private final String status;
    }

    /**
     * 일괄 처리 가능 여부 응답 DTO (내부 클래스)
     */
    @lombok.Builder
    @lombok.Getter
    public static class BulkEligibilityResponse {
        private final Long concertId;
        private final Long userId;
        private final int totalReservedSeats;
        private final int lockEligibleSeats;
        private final int restoreEligibleSeats;
        private final boolean canBulkLock;
        private final boolean canBulkRestore;

        /**
         * 일괄 처리 가능 여부 요약 메시지
         */
        public String getSummary() {
            return String.format(
                    "전체 선점: %d석, 영구선점가능: %d석, 복원가능: %d석",
                    totalReservedSeats, lockEligibleSeats, restoreEligibleSeats
            );
        }
    }
}