package com.team03.ticketmon.queue.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.queue.adapter.QueueRedisAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


/**
 * 사용자의 '입장(Admission)'과 관련된 핵심 비즈니스 로직을 처리하는 서비스
 * - 원자적인 슬롯 점유 시도
 * - 여러 사용자에 대한 동시 입장 처리 (AccessKey 발급, 세션 등록 등)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionService {
    private final RedissonClient redissonClient;
    private final RedisKeyGenerator keyGenerator;

    private final NotificationService notificationService;
    private final QueueRedisAdapter queueRedisAdapter;

    @Value("${app.queue.access-key-ttl-seconds}")
    private long accessKeyTtlSeconds; // 발급된 입장 허가 키의 유효 시간 (분)
    @Value("${app.queue.max-active-users}")
    private long maxActiveUsers;

    /**
     * 단일 사용자 즉시 입장
     * 내부적으로 리스트 처리 메서드를 호출하여 코드 중복 방지
     *
     * @param concertId 입장시킬 콘서트 ID
     * @param userId 입장시킬 사용자 ID
     * @return 발급된 AccessKey
     */
    public String grantAccess(Long concertId, Long userId) {
        List<String> accessKeys = grantAccess(concertId, List.of(userId), false, false);
        return accessKeys.isEmpty() ? null : accessKeys.get(0);
    }

    /**
     * 여러 사용자의 입장을 처리하고, 선택적으로 알림을 전송
     * Redis 파이프라이닝을 활용하기 위해 RBatch를 사용하여 여러 명령을 한 번에 전송
     *
     * @param userIds 입장시킬 사용자 ID 리스트
     * @param sendNotification Redis Pub/Sub으로 알림을 보낼지 여부
     * @return 발급된 AccessKey 리스트
     */
    public List<String> grantAccess(Long concertId, List<Long> userIds, boolean sendNotification, boolean incrementCounter) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("{}명의 신규 사용자 입장 처리 시작. 알림 발송: {}", userIds.size(), sendNotification);

        String activeSessionsKey = keyGenerator.getActiveSessionsKey(concertId);
        String activeUserCountKey = keyGenerator.getActiveUsersCountKey(concertId);

        long expiryTimestamp = System.currentTimeMillis() + (accessKeyTtlSeconds * 1000);
        Duration ttl = Duration.ofSeconds(accessKeyTtlSeconds);

        List<String> issuedKeys = new ArrayList<>();

        // RBatch를 사용하여 여러 명령을 원자적으로 실행 (파이프라이닝 효과로 성능 향상)
        RBatch batch = redissonClient.createBatch();

        for (Long userId : userIds) {
            String accessKey = UUID.randomUUID().toString();
            issuedKeys.add(accessKey);

            // 1. AccessKey 저장
            String accessKeyRedisKey = keyGenerator.getAccessKey(concertId, userId);
            batch.getBucket(accessKeyRedisKey).setAsync(accessKey, ttl);

            // 2. 만료 시간 관리를 위해 active_sessions Sorted Set에 추가 (Score: 만료시간, Value: userId)
            batch.getScoredSortedSet(activeSessionsKey, LongCodec.INSTANCE).addAsync(expiryTimestamp, userId);

            // 3. 알림이 필요한 경우 (스케줄러에 의해 호출될 때) 알림 전송
            if (sendNotification) {
                notificationService.sendAdmissionNotification(userId, accessKey);
            }
        }

        // 4. 활성 사용자 수 조건부로 원자적으로 증가
        if (incrementCounter) {
            batch.getAtomicLong(activeUserCountKey).addAndGetAsync(userIds.size());
        }

        // 5. 준비된 모든 명령을 Redis 서버로 한 번에 전송
        try {
            batch.execute();
            log.info("{}명 입장 처리 배치 작업 성공. 현재 총 활성 사용자 수: {}",
                    userIds.size(), redissonClient.getAtomicLong(activeUserCountKey).get());
        } catch (Exception e) {
            log.error("[BATCH_EXECUTE_FAILED] 사용자 ID 리스트 {} 입장 처리 중 배치 실행 실패", userIds, e);
            throw new BusinessException(ErrorCode.REDIS_COMMAND_FAILED, "입장 처리 중 시스템 오류가 발생");
        }

        return issuedKeys;
    }

    /**
     * 즉시 입장을 위해 슬롯이 남아있는지 확인하고 원자적으로 점유를 시도
     * 'Compare-And-Set' (CAS) 연산을 사용하여 여러 스레드가 동시에 접근해도 경쟁 상태(Race Condition)를 방지
     *
     * @param concertId 점유를 시도할 콘서트 ID
     * @return 슬롯 점유에 성공하면 true, 실패하면 false
     */
    public boolean tryClaimSlot(Long concertId) {
        RAtomicLong activeUsersCount = queueRedisAdapter.getActiveUserCounter(concertId);

        while (true) {
            long current = activeUsersCount.get();
            if (current >= maxActiveUsers) {
                return false; // 슬롯 점유 실패
            }
            if (activeUsersCount.compareAndSet(current, current + 1)) {
                return true; // 슬롯 점유 성공!
            }
        }
    }
}