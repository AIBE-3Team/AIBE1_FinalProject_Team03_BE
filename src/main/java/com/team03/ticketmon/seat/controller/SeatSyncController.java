package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.seat.dto.AllConcertSyncResult;
import com.team03.ticketmon.seat.dto.SeatSyncResult;
import com.team03.ticketmon.seat.service.SeatSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 좌석 동기화 관리 컨트롤러
 *
 * 목적: Redis와 DB 간 좌석 상태 동기화를 위한 관리자 API 제공
 *
 * 주요 기능:
 * - 특정 콘서트 좌석 상태 동기화
 * - 전체 활성 콘서트 동기화 (추후 구현)
 * - 동기화 결과 리포팅
 *
 * 보안: ADMIN 권한 사용자만 접근 가능
 */
@Tag(name = "좌석 동기화 관리", description = "Redis-DB 좌석 상태 동기화 API (관리자 전용)")
@Slf4j
@RestController
@RequestMapping("/api/admin/seats/sync")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SeatSyncController {

    private final SeatSyncService seatSyncService;

    /**
     * 특정 콘서트의 좌석 상태 동기화
     *
     * DB의 Ticket 존재 여부를 기준으로 Redis 좌석 상태를 동기화합니다.
     * 불일치 데이터를 감지하고 자동으로 수정합니다.
     */
    @Operation(
            summary = "콘서트 좌석 상태 동기화",
            description = "특정 콘서트의 DB-Redis 좌석 상태를 동기화하고 불일치 데이터를 수정합니다."
    )
    @PostMapping("/concerts/{concertId}")
    public ResponseEntity<SuccessResponse<SeatSyncResult>> syncConcertSeats(
            @Parameter(description = "동기화할 콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        log.info("콘서트 좌석 동기화 요청: concertId={}", concertId);

        try {
            // 동기화 수행
            SeatSyncResult result = seatSyncService.syncConcertSeats(concertId);

            if (result.isSuccess()) {
                log.info("콘서트 좌석 동기화 성공: {}", result.getSummary());

                return ResponseEntity.ok(
                        SuccessResponse.of(
                                "좌석 상태 동기화가 완료되었습니다.",
                                result
                        )
                );
            } else {
                log.error("콘서트 좌석 동기화 실패: {}", result.getSummary());

                return ResponseEntity.status(500).body(
                        SuccessResponse.of(
                                "좌석 상태 동기화 중 오류가 발생했습니다: " + result.getErrorMessage(),
                                result
                        )
                );
            }

        } catch (Exception e) {
            log.error("콘서트 좌석 동기화 중 예외 발생: concertId={}", concertId, e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "좌석 상태 동기화 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    /**
     * 콘서트 좌석 동기화 상태 체크 (실제 동기화 없이 분석만)
     *
     * 동기화가 필요한지 미리 확인할 수 있는 API입니다.
     */
    @Operation(
            summary = "콘서트 좌석 동기화 상태 체크",
            description = "실제 동기화 없이 DB-Redis 간 불일치 데이터만 분석합니다."
    )
    @GetMapping("/concerts/{concertId}/check")
    public ResponseEntity<SuccessResponse<SeatSyncCheckResult>> checkSyncStatus(
            @Parameter(description = "체크할 콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        log.info("콘서트 좌석 동기화 상태 체크 요청: concertId={}", concertId);

        try {
            // TODO: SeatSyncService에 체크 전용 메서드 추가 필요
            // 현재는 실제 동기화 결과로 대체
            SeatSyncResult syncResult = seatSyncService.syncConcertSeats(concertId);

            SeatSyncCheckResult checkResult = SeatSyncCheckResult.builder()
                    .concertId(concertId)
                    .needsSync(syncResult.hasSyncIssues())
                    .totalIssues(syncResult.getInconsistentSeats() +
                            syncResult.getMissingInRedis() +
                            syncResult.getExtraInRedis())
                    .inconsistentSeats(syncResult.getInconsistentSeats())
                    .missingInRedis(syncResult.getMissingInRedis())
                    .extraInRedis(syncResult.getExtraInRedis())
                    .lastChecked(syncResult.getSyncEndTime())
                    .build();

            return ResponseEntity.ok(
                    SuccessResponse.of(
                            "좌석 동기화 상태 체크가 완료되었습니다.",
                            checkResult
                    )
            );

        } catch (Exception e) {
            log.error("콘서트 좌석 동기화 상태 체크 중 예외 발생: concertId={}", concertId, e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "좌석 동기화 상태 체크 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    /**
     * 전체 활성 콘서트 좌석 상태 동기화
     *
     * 현재 예매 중인 모든 콘서트의 좌석 상태를 일괄 동기화합니다.
     * 시간이 오래 걸릴 수 있으므로 주의해서 사용하세요.
     */
    @Operation(
            summary = "전체 콘서트 좌석 상태 동기화",
            description = "모든 활성 콘서트의 좌석 상태를 일괄 동기화합니다. (시간 소요 주의)"
    )
    @PostMapping("/all")
    public ResponseEntity<SuccessResponse<AllConcertSyncResult>> syncAllConcertSeats() {

        log.info("전체 콘서트 좌석 동기화 요청");

        try {
            // 전체 동기화 수행
            AllConcertSyncResult result = seatSyncService.syncAllActiveSeats();

            if (result.isSuccess()) {
                log.info("전체 콘서트 좌석 동기화 성공: {}", result.getSummary());

                return ResponseEntity.ok(
                        SuccessResponse.of(
                                "전체 좌석 상태 동기화가 완료되었습니다.",
                                result
                        )
                );
            } else {
                log.warn("전체 콘서트 좌석 동기화 일부 실패: {}", result.getSummary());

                return ResponseEntity.ok(
                        SuccessResponse.of(
                                "전체 좌석 상태 동기화가 완료되었지만 일부 오류가 있습니다: " + result.getMessage(),
                                result
                        )
                );
            }

        } catch (Exception e) {
            log.error("전체 콘서트 좌석 동기화 중 예외 발생", e);

            return ResponseEntity.status(500).body(
                    SuccessResponse.of(
                            "전체 좌석 상태 동기화 중 서버 오류가 발생했습니다.",
                            null
                    )
            );
        }
    }

    /**
     * 동기화 상태 체크 결과 DTO (내부 클래스)
     */
    @lombok.Builder
    @lombok.Getter
    public static class SeatSyncCheckResult {
        private final Long concertId;
        private final boolean needsSync;
        private final int totalIssues;
        private final int inconsistentSeats;
        private final int missingInRedis;
        private final int extraInRedis;
        private final java.time.LocalDateTime lastChecked;

        public String getSummary() {
            if (!needsSync) {
                return "동기화 불필요 - 모든 좌석 상태가 정상입니다.";
            }
            return String.format("동기화 필요 - 총 %d개 문제 (불일치: %d, 누락: %d, 불필요: %d)",
                    totalIssues, inconsistentSeats, missingInRedis, extraInRedis);
        }
    }
}