package com.team03.ticketmon.queue.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final RedissonClient redissonClient;
    private static final String ACTIVE_USERS_COUNT_KEY = "active_users_count";
    private static final String ACTIVE_SESSIONS_KEY = "active_sessions";


    @Scheduled(fixedDelay = 10000)
    public void cleanupExpiredSessions() {
        log.info("===== CleanupScheduler - 만료된 세션 정리 작업 시작=====");

        // 1. active_sessions Set을 가져온다.
        RScoredSortedSet<String> activeSessions = redissonClient.getScoredSortedSet(ACTIVE_SESSIONS_KEY);

        // 2. 현재 시간보다 점수(만료 시간)가 낮은 모든 멤버를 찾아 삭제한다.
        int expiredCount = activeSessions.removeRangeByScore(0, true, System.currentTimeMillis(), true);

        if (expiredCount > 0) {
            // 3. 삭제된 수만큼 활성 사용자 수를 감소시킨다.
            long newCount = redissonClient.getAtomicLong(ACTIVE_USERS_COUNT_KEY).addAndGet(-expiredCount);
            log.info("{}개의 만료된 세션을 정리했습니다. 현재 활성 사용자 수: {}", expiredCount, newCount);
        } else {
            log.info("만료된 세션이 없습니다.");
        }
        log.info("===== CleanupScheduler - 만료된 세션 정리 작업 종료=====");
    }
}