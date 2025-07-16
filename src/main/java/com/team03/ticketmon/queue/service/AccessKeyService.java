package com.team03.ticketmon.queue.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.queue.adapter.QueueRedisAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessKeyService {

    private final QueueRedisAdapter queueRedisAdapter;

    @Value("${app.queue.access-key-extend-seconds}")
    private long accessKeyExtendSeconds;

    /**
     * 액세스 키의 유효 시간을 연장
     * '최종 만료 시각'을 기준으로 최대 세션 시간을 절대 넘지 않도록 제어
     *
     * @param concertId 연장할 콘서트 ID
     * @param userId    연장할 사용자 ID
     * @return 새롭게 설정된 유효 시간(초)
     */
    public long extendAccessKey(Long concertId, Long userId) {
        RBucket<String> accessKeyBucket = queueRedisAdapter.getAccessKeyBucket(concertId, userId);
        RBucket<Long> finalExpiryBucket = queueRedisAdapter.getFinalExpiryBucket(concertId, userId);

        if (!accessKeyBucket.isExists() || !finalExpiryBucket.isExists()) {
            throw new BusinessException(ErrorCode.INVALID_ACCESS_KEY, "연장할 수 있는 유효한 키가 없습니다.");
        }

        // 1. 세션의 최종만료시간-현재시간 계산
        long finalExpiryTimestamp = finalExpiryBucket.get();
        long remainingLifeSeconds = (finalExpiryTimestamp - System.currentTimeMillis()) / 1000L;

        if (remainingLifeSeconds <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ACCESS_KEY, "세션이 만료되었습니다.");
        }

        // 2. 목표 TTL 결정
        long targetTtlSeconds = Math.min(accessKeyExtendSeconds, remainingLifeSeconds);

        // 3. 현재 키에 실제로 남아있는 TTL 조회
        long currentTtlSeconds = TimeUnit.MILLISECONDS.toSeconds(accessKeyBucket.remainTimeToLive());
        long appliedTtl = currentTtlSeconds;

        // 4. 목표 TTL이 현재 남은 TTL보다 클 경우에만 유효 시간을 갱신 (단축 방지)
        if (appliedTtl > 5000L && targetTtlSeconds > currentTtlSeconds) {
            accessKeyBucket.expire(Duration.ofSeconds(targetTtlSeconds));

            RScoredSortedSet<Long> activeSessions = queueRedisAdapter.getActiveSessions(concertId);
            long newScoreTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(targetTtlSeconds);
            activeSessions.add(newScoreTimestamp, userId);

            appliedTtl = targetTtlSeconds;
            log.info("[AccessKey] 키 연장 완료. userId: {}, newTTL: {}초", userId, appliedTtl);
        }

        return appliedTtl;
    }


    /**
     * 사용자의 세션을 '만료 예정'으로 표시하여 CleanupScheduler가 처리하도록 위임합니다.
     */
    public void invalidateAccessKey(Long concertId, Long userId) {
        queueRedisAdapter.getActiveSessions(concertId).add(0, userId);
        log.info("[AccessKey] 키 만료 처리 요청 완료. userId: {}", userId);
    }
}