package com.team03.ticketmon.seat.scheduler;

import com.team03.ticketmon.seat.dto.SeatSyncResult;
import com.team03.ticketmon.seat.service.SeatSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 좌석 상태 동기화 스케줄러
 *
 * 목적: 주기적으로 Redis와 DB 간 좌석 상태 동기화를 자동 수행
 *
 * 주요 기능:
 * - 설정 가능한 주기로 동기화 작업 실행
 * - 분산 락을 통한 중복 실행 방지
 * - 동기화 결과 로깅 및 모니터링
 *
 * 설정:
 * - app.scheduler.seat-sync.enabled: 스케줄러 활성화 여부
 * - 실행 주기: 매 30분마다 (설정 변경 가능)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "app.scheduler.seat-sync.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class SeatSyncScheduler {

    private final SeatSyncService seatSyncService;
    private final RedissonClient redissonClient;

    // 분산 락 설정
    private static final String SYNC_LOCK_KEY = "seat:sync:lock";
    private static final long LOCK_WAIT_TIME = 5; // 락 획득 대기 시간 (초)
    private static final long LOCK_LEASE_TIME = 1800; // 락 보유 시간 (30분)

//    /**
//     * 주기적 좌석 상태 동기화 작업
//     *
//     * 실행 주기: 매 30분마다 (cron: 0 */30 * * * *)
//     * - 0분, 30분에 실행
//     *
//     * 분산 락을 사용하여 다중 서버 환경에서도 중복 실행을 방지합니다.
//     */
    @Scheduled(cron = "0 */30 * * * *") // 매 30분마다 실행
    public void syncSeatStatesScheduled() {
        log.info("주기적 좌석 상태 동기화 작업 시작: {}", LocalDateTime.now());

        RLock lock = redissonClient.getLock(SYNC_LOCK_KEY);

        try {
            // 분산 락 획득 시도
            boolean lockAcquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!lockAcquired) {
                log.warn("좌석 동기화 스케줄러 락 획득 실패 - 다른 서버에서 이미 실행 중일 수 있습니다.");
                return;
            }

            log.info("좌석 동기화 스케줄러 락 획득 성공 - 동기화 작업 시작");

            // 실제 동기화 작업 수행
            performScheduledSync();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("좌석 동기화 스케줄러 인터럽트 발생", e);
        } catch (Exception e) {
            log.error("좌석 동기화 스케줄러 실행 중 예외 발생", e);
        } finally {
            // 락 해제 (안전한 해제)
            if (lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.debug("좌석 동기화 스케줄러 락 해제 완료");
                } catch (Exception e) {
                    log.error("좌석 동기화 스케줄러 락 해제 중 오류", e);
                }
            }
        }
    }

    /**
     * 실제 동기화 작업 수행
     *
     * 현재는 전체 활성 콘서트 동기화를 수행합니다.
     * 추후 특정 조건(예: 최근 활동이 있는 콘서트만)으로 최적화 가능합니다.
     */
    private void performScheduledSync() {
        LocalDateTime syncStartTime = LocalDateTime.now();

        try {
            log.info("스케줄된 전체 좌석 동기화 시작");

            // TODO: 현재는 개별 콘서트 동기화만 구현됨
            // 전체 콘서트 동기화 기능 완성되면 활성화
            // AllConcertSyncResult result = seatSyncService.syncAllActiveSeats();

            // 임시: 로그만 출력
            log.info("전체 좌석 동기화 기능은 추후 구현 예정 (개별 콘서트 동기화는 API로 사용 가능)");

            // 예시: 특정 콘서트만 동기화 (테스트용)
            // syncSpecificConcerts();

            LocalDateTime syncEndTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(syncStartTime, syncEndTime).toMillis();

            log.info("스케줄된 좌석 동기화 완료 - 소요시간: {}ms", durationMs);

        } catch (Exception e) {
            log.error("스케줄된 좌석 동기화 중 오류 발생", e);
        }
    }

    /**
     * 특정 콘서트들의 동기화 수행 (예시 구현)
     *
     * 실제 운영에서는 다음과 같은 조건으로 필터링할 수 있습니다:
     * - 현재 예매 중인 콘서트
     * - 최근 1시간 내 좌석 상태 변경이 있었던 콘서트
     * - 특정 임계값 이상의 불일치가 예상되는 콘서트
     */
    private void syncSpecificConcerts() {
        // TODO: 실제 구현 시 활성 콘서트 목록 조회 로직 필요

        // 예시: 하드코딩된 콘서트 ID들 (실제로는 DB에서 조회)
        Long[] testConcertIds = {1L, 2L, 3L};

        for (Long concertId : testConcertIds) {
            try {
                log.debug("개별 콘서트 동기화 시작: concertId={}", concertId);

                SeatSyncResult result = seatSyncService.syncConcertSeats(concertId);

                if (result.isSuccess()) {
                    if (result.hasSyncIssues()) {
                        log.info("콘서트 동기화 완료 (문제 해결): {}", result.getSummary());
                    } else {
                        log.debug("콘서트 동기화 완료 (문제 없음): concertId={}", concertId);
                    }
                } else {
                    log.error("콘서트 동기화 실패: {}", result.getSummary());
                }

            } catch (Exception e) {
                log.error("개별 콘서트 동기화 중 오류: concertId={}", concertId, e);
            }
        }
    }

    /**
     * 긴급 동기화 작업 (수동 트리거용)
     *
     * 특별한 상황에서 즉시 동기화가 필요한 경우 사용할 수 있는 메서드입니다.
     * 스케줄러 외부에서 호출 가능하도록 public으로 제공합니다.
     *
     * @param concertId 긴급 동기화할 콘서트 ID
     * @return 동기화 결과
     */
    public SeatSyncResult performEmergencySync(Long concertId) {
        log.warn("긴급 좌석 동기화 요청: concertId={}", concertId);

        try {
            SeatSyncResult result = seatSyncService.syncConcertSeats(concertId);
            log.info("긴급 좌석 동기화 완료: {}", result.getSummary());
            return result;

        } catch (Exception e) {
            log.error("긴급 좌석 동기화 실패: concertId={}", concertId, e);
            throw e;
        }
    }

    /**
     * 스케줄러 상태 확인
     *
     * @return 스케줄러 활성화 여부 및 마지막 실행 정보
     */
    public String getSchedulerStatus() {
        // TODO: 실제 서비스 시 마지막 실행 시간, 성공/실패 횟수 등 추가
        return String.format("좌석 동기화 스케줄러 - 활성화됨, 실행주기: 30분마다, 현재시간: %s",
                LocalDateTime.now());
    }
}