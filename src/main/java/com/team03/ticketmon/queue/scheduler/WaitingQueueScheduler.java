package com.team03.ticketmon.queue.scheduler;

import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.queue.adapter.QueueRedisAdapter;
import com.team03.ticketmon.queue.service.AdmissionService;
import com.team03.ticketmon.queue.service.WaitingQueueService;
import com.team03.ticketmon.queue.strategy.PersonalizedRankStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 주기적으로 대기열을 확인하여 입장 가능 인원을 처리하는 스케줄러.
 * 이 스케줄러는 시스템의 처리량을 조절하는 핵심적인 역할을 담당하며,
 * 분산 환경에서도 단 하나의 인스턴스만 실행되도록 분산 락(Distributed Lock)을 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueScheduler {

    private final WaitingQueueService waitingQueueService;
    private final ConcertRepository concertRepository;
    private final AdmissionService admissionService;
    private final QueueRedisAdapter queueRedisAdapter;
    private final PersonalizedRankStrategy personalizedRankStrategy;

    @Value("${app.queue.max-active-users}")
    private long maxActiveUsers; // 시스템이 동시에 수용 가능한 최대 활성 사용자 수

    /**
     * 10초마다 주기적으로 실행되어 대기열을 처리.
     * fixedDelay는 이전 작업이 성공적으로 끝난 후 10초를 기다리는 것을 의미.
     * 분산 락을 사용하여 여러 인스턴스 중 하나만 이 메서드를 실행하도록 보장.
     */
    @Scheduled(fixedDelay = 10000)
    public void execute() {

        // 분산 락 획득 시도
        RLock lock = queueRedisAdapter.getAdmissionSchedulerLock();

        try {
            // [분산 락 획득 시도]
            // waitTime(0): waitTime을 0으로 두어, 락 획득에 실패하면 즉시 리턴하는 비대기 모드는 유지
            // leaseTime(-1): 워치독(락 자동 갱신) 기능을 활성화. 락을 획득하면 기본 30초의 TTL이 설정되고, 작업이 끝나기 전까지 워치독이 락을 계속 갱신
            boolean isLocked = lock.tryLock(0, -1, TimeUnit.SECONDS);

            if (!isLocked) {
                log.debug("===== 다른 대기열 스케줄러 인스턴스에서 스케줄러가 실행 중이므로, 현재 스케줄러는 건너뜁니다.");
                return;
            }

            // [STEP 1] 현재 처리해야 할 모든 활성 콘서트 ID 목록을 조회
            List<Long> activeConcertIds = concertRepository.findConcertIdsByStatus(ConcertStatus.ON_SALE);

            if (activeConcertIds.isEmpty()) {
                log.debug("===== 현재 처리할 ON_SALE 상태의 콘서트가 없습니다.");
                return;
            }

            log.info("===== 대기열 스케줄러 실행 시작 (처리 대상 콘서트 대기열: {}개) =====", activeConcertIds.size());

            // [STEP 2] 각 콘서트 ID에 대해 대기열 처리 로직을 실행
            for (Long concertId : activeConcertIds) {
                processQueueForConcert(concertId);
            }

        } catch (InterruptedException e) {
            // 스레드가 중단 신호를 받으면, 현재 스레드의 중단 상태를 다시 설정하여 상위 코드가 인지할 수 있도록 함.
            Thread.currentThread().interrupt();
            log.error("===== 대기열 스케줄러 분산 락을 획득하는 동안 인터럽트 발생", e);
        } finally {
            // 현재 스레드가 락을 점유하고 있는 경우에만 해제를 시도하여, 다른 스레드가 획득한 락을 해제하는 실수를 방지.
            if  (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            log.info("===== 대기열 스케줄러 실행 종료 =====");
        }
    }

    /**
     * 특정 콘서트 ID에 대한 대기열 처리 로직
     * @param concertId 처리할 콘서트의 ID
     */
    private void processQueueForConcert(Long concertId) {
        log.debug("===== [콘서트 ID: {}] 대기열 처리 시작. =====", concertId);

        // ==================== 1. 입장 처리 로직 ====================
        RAtomicLong activeUsersCount = queueRedisAdapter.getActiveUserCounter(concertId);
        long currentActiveUsers = activeUsersCount.get();
        // TODO: maxActiveUsers도 콘서트별로 다르게 설정할 수 있도록 DB에서 가져오는 로직 추가 가능 (우선순위: 최하)
        long availableSlots = maxActiveUsers - currentActiveUsers;

        if (availableSlots <= 0) {
            log.debug("===== [콘서트 ID: {}] 입장 가능한 자리가 없습니다. 대기열 처리 스킵 =====", concertId);
            return;
        }

        log.debug("===== [콘서트 ID: {}] 활성 사용자 현황: {} / {} (빈자리: {}) =====", concertId, currentActiveUsers, maxActiveUsers, availableSlots);

        // 해당 콘서트 대기열에서 빈자리 수만큼 사용자를 원자적으로 추출
        List<Long> admittedUserIds = waitingQueueService.poll(concertId, (int) availableSlots);

        if (admittedUserIds.isEmpty()) {
            log.debug("===== [콘서트 ID: {}] 새로 입장할 대기 인원이 없습니다. 스케줄러 작업을 종료 =====", concertId);
            return;
        }

        // 추출된 사용자들에게 입장 허가 처리
        admissionService.grantAccess(concertId, admittedUserIds, true, true);

        // ==================== 2. 알림 로직 실행 ====================
        RScoredSortedSet<Long> queue = queueRedisAdapter.getQueue(concertId);

        if (queue != null && !queue.isEmpty()) {
            log.debug("[Notification] 콘서트 ID {}: 알림 전략 실행.", concertId);
            personalizedRankStrategy.execute(concertId, queue);
        }
    }
}