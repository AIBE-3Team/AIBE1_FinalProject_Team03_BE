package com.team03.ticketmon.queue.scheduler;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
     * TODO: 설정(cron, lock 타임아웃 등) application.yml 분리
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

            // TODO [성능개선]: 활성화된 대기열 ID를 DB가 아니라 Redis에서 직접 조회하는 방법 검토
            concertRepository.findConcertIdsByStatus(ConcertStatus.ON_SALE)
                    .forEach(this::syncConcertCounts);

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

    // TODO [배치처리]: 네트워크 왕복 최소화를 위해 Redisson Batch API 적용
    private void syncConcertCounts(Long concertId) {
        String activeSessionsKey = keyGenerator.getActiveSessionsKey(concertId);
        String countKey = keyGenerator.getActiveUsersCountKey(concertId);

        RAtomicLong counter = redissonClient.getAtomicLong(countKey);

        long actualSessionSize = redissonClient.getScoredSortedSet(activeSessionsKey).size();
        long storedCnt  = counter.get();

        if (actualSessionSize != storedCnt) {
            log.warn("[콘서트 ID: {}] 불일치: counter={}, 실제={}. 동기화 수행",
                    concertId, storedCnt, actualSessionSize);
            counter.set(actualSessionSize);
            // TODO [메트릭]: 동기화 발생 건수 카운팅 추가 (예: meterRegistry.counter("sync.count").increment())
        }
    }
}