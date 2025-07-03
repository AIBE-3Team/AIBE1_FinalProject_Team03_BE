package com.team03.ticketmon.queue.scheduler;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsistencyCheckScheduler {

    private final RedissonClient redissonClient;
    private final ConcertRepository concertRepository;
    private final RedisKeyGenerator keyGenerator;

    /**
     * 1시간마다 실행되어 활성 사용자 수와 실제 세션 수의 정합성을 체크하고 보정합니다.
     */
    @Scheduled(cron = "0 0 * * * *") // 매시 정각에 실행
    public void checkAndSyncCounts() {
        RLock lock = redissonClient.getLock(RedisKeyGenerator.CONSISTENCY_CHECK_LOCK_KEY);

        try {
            boolean isLocked = lock.tryLock(10, 60, TimeUnit.SECONDS);
            if (!isLocked) {
                log.info("다른 인스턴스에서 정합성 체크 스케줄러가 실행 중입니다.");
                return;
            }

            log.info("===== 데이터 정합성 체크 스케줄러 시작 =====");

            List<Long> activeConcertIds = concertRepository.findConcertIdsByStatus(ConcertStatus.ON_SALE);

            for (Long concertId : activeConcertIds) {
                String activeSessionsKey = keyGenerator.getActiveSessionsKey(concertId);
                String activeUserCountKey = keyGenerator.getActiveUsersCountKey(concertId);

                // 1. 실제 세션 Set의 크기를 조회 (이것이 진실)
                long actualSessionSize = redissonClient.getScoredSortedSet(activeSessionsKey).size();

                // 2. 카운터의 현재 값을 조회
                long currentCounterValue = redissonClient.getAtomicLong(activeUserCountKey).get();

                // 3. 두 값이 다르면, 카운터를 실제 세션 크기로 강제 동기화
                if (actualSessionSize != currentCounterValue) {
                    log.warn("[콘서트 ID: {}] 데이터 불일치 발견! 카운터: {}, 실제 세션 수: {}. 강제 동기화를 실행합니다.",
                            concertId, currentCounterValue, actualSessionSize);
                    redissonClient.getAtomicLong(activeUserCountKey).set(actualSessionSize);
                }
            }
            log.info("===== 데이터 정합성 체크 스케줄러 종료 =====");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("정합성 체크 스케줄러 락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}