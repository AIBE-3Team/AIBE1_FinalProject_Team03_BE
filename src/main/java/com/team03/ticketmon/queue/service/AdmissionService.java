package com.team03.ticketmon.queue.service;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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


@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionService {
    private final RedissonClient redissonClient;
    private final NotificationService notificationService;
    private final RedisKeyGenerator keyGenerator;

    @Value("${app.access-key-ttl-minutes}")
    private long accessKeyTtlMinutes; // 발급된 입장 허가 키의 유효 시간 (분)

    /**
     * 단일 사용자를 즉시 입장시킵니다.
     * @param userId 사용자 ID
     * @return 발급된 AccessKey
     */
    public String grantAccess(Long concertId, Long userId) {
        // 내부적으로 리스트 처리 메서드를 호출하여 코드 중복을 방지합니다.
        List<String> accessKeys = grantAccess(concertId, List.of(userId), false); // 즉시 입장은 알림을 보내지 않음
        return accessKeys.isEmpty() ? null : accessKeys.get(0);
    }

    /**
     * 여러 사용자의 입장을 처리하고, 선택적으로 알림을 보냅니다.
     * @param userIds 입장시킬 사용자 ID 리스트
     * @param sendNotification Redis Pub/Sub으로 알림을 보낼지 여부
     * @return 발급된 AccessKey 리스트
     */
    public List<String> grantAccess(Long concertId, List<Long> userIds, boolean sendNotification) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("{}명의 신규 사용자 입장 처리 시작. 알림 발송: {}", userIds.size(), sendNotification);

        String activeSessionsKey = keyGenerator.getActiveSessionsKey(concertId);
        String activeUserCountKey = keyGenerator.getActiveUsersCountKey(concertId);

        long expiryTimestamp = System.currentTimeMillis() + (accessKeyTtlMinutes * 60 * 1000);
        Duration ttl = Duration.ofMinutes(accessKeyTtlMinutes);

        List<String> issuedKeys = new ArrayList<>();

        // RBatch를 사용하여 여러 명령을 원자적으로 실행 (파이프라이닝 효과로 성능 향상)
        RBatch batch = redissonClient.createBatch();

        for (Long userId : userIds) {
            String accessKey = UUID.randomUUID().toString();
            issuedKeys.add(accessKey);

            // 1. AccessKey 저장
            String accessKeyRedisKey = keyGenerator.getAccessKey(concertId, userId);
            batch.getBucket(accessKeyRedisKey).setAsync(accessKey, ttl);
            // 2. 만료 시간 관리를 위해 active_sessions에 추가
            batch.getScoredSortedSet(activeSessionsKey, LongCodec.INSTANCE).addAsync(expiryTimestamp, userId);

            // 3. 알림이 필요한 경우 (스케줄러에 의해 호출될 때) 알림 전송
            if (sendNotification) {
                notificationService.sendAdmissionNotification(userId, accessKey);
            }
        }

        // 4. 활성 사용자 수 원자적으로 증가
        batch.getAtomicLong(activeUserCountKey).addAndGetAsync(userIds.size());

        // 배치 작업 실행
        batch.execute();

        log.debug("{}명 입장 처리 완료. 현재 총 활성 사용자 수: {}", userIds.size(), redissonClient.getAtomicLong(activeUserCountKey).get());
        return issuedKeys;
    }
}