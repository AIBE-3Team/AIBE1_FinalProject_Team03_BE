package com.team03.ticketmon.queue.scheduler;

import com.team03.ticketmon.queue.service.NotificationService;
import com.team03.ticketmon.queue.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 주기적으로 대기열을 확인하여 입장 가능 인원을 처리하는 스케줄러입니다.
 * 이 스케줄러는 시스템의 처리량을 조절하는 핵심적인 역할을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueScheduler {

    private static final String ACTIVE_SESSIONS_KEY = "active_sessions";
    private final WaitingQueueService waitingQueueService;
    private final RedissonClient redissonClient;
    private final NotificationService notificationService;

    @Value("${app.max-active-users}")
    private long maxActiveUsers; // 시스템이 동시에 수용 가능한 최대 활성 사용자 수
    @Value("${app.access-key-ttl-minutes}")
    private long accessKeyTtlMinutes; // 발급된 입장 허가 키의 유효 시간 (분)

    private static final String ACCESS_KEY_PREFIX = "accesskey:"; // 사용자별 입장 허가 키 (String)
    private static final String ACTIVE_USERS_COUNT_KEY = "active_users_count"; // 현재 활성 사용자 수 (AtomicLong)

    // TODO: 현재는 단일 콘서트를 가정하여 ID를 1L로 고정했지만, 향후 여러 콘서트를 지원하려면 이 로직을 동적으로 변경해야함
    private static final long CONCERT_ID = 1L;


    /**
     * 10초마다 주기적으로 실행되어 대기열을 처리합니다.
     * fixedDelay는 이전 작업이 끝난 후 10초를 기다리는 것을 의미합니다.
     */
    @Scheduled(fixedDelay = 10000)
    public void execute() {
        log.info("===== 대기열 스케줄러 실행 시작 =====");

        // 현재 시스템의 활성 사용자 수를 Redis에서 조회
        RAtomicLong activeUsersCount = redissonClient.getAtomicLong(ACTIVE_USERS_COUNT_KEY);
        long currentActiveUsers = activeUsersCount.get();

        // 입장 가능한 빈자리(slot)를 계산
        long availableSlots = maxActiveUsers - currentActiveUsers;

//        log.debug("활성 사용자 현황: {} / {} (빈자리: {})", currentActiveUsers, maxActiveUsers, availableSlots);
        log.info("활성 사용자 현황: {} / {} (빈자리: {})", currentActiveUsers, maxActiveUsers, availableSlots);

        if (availableSlots <= 0) {
            log.info("===== 입장 가능한 자리가 없습니다. 스케줄러 작업을 종료 =====");
            return;
        }


        // 대기열에서 빈자리 수만큼 사용자를 추출
        List<String> admittedUsers = waitingQueueService.poll(CONCERT_ID, (int) availableSlots);

        if (admittedUsers.isEmpty()) {
            log.info("===== 새로 입장할 대기 인원이 없습니다. 스케줄러 작업을 종료 =====");
            return;
        }

        // 추출된 사용자들에게 입장을 허가
        grantAccessToUsers(admittedUsers);

        log.info("===== 대기열 스케줄러 실행 종료 =====");
    }

    /**
     * 입장 허가된 사용자들에게 고유 키를 발급하고, 알림을 전송한 후, 활성 사용자 수를 업데이트합니다.
     *
     * @param admittedUsers 입장 허가된 사용자 ID 리스트
     */
    private void grantAccessToUsers(List<String> admittedUsers) {
        log.info("{}명의 신규 사용자 입장 처리 시작: {}", admittedUsers.size(), admittedUsers);

        RScoredSortedSet<String> activeSessions = redissonClient.getScoredSortedSet(ACTIVE_SESSIONS_KEY);
        long expiryTimestamp = System.currentTimeMillis() + accessKeyTtlMinutes * 60 * 1000;


        for (String userId : admittedUsers) {
            // 1. 사용자별로 예측 불가능한 고유 AccessKey를 발급
            String accessKey = UUID.randomUUID().toString();
            RBucket<String> accessKeyBucket = redissonClient.getBucket(ACCESS_KEY_PREFIX + userId);

            // 2. Redis에 AccessKey를 TTL(Time-To-Live)과 함께 저장하여 일정 시간 후 자동 만료되도록
            accessKeyBucket.set(accessKey, Duration.ofMinutes(accessKeyTtlMinutes));
//            log.debug("사용자 '{}'에게 접근 키 발급 및 저장 완료 (유효 시간: {}분)", userId, accessKeyTtlMinutes);
            log.info("사용자 '{}'에게 접근 키 발급 및 저장 완료 (유효 시간: {}분)", userId, accessKeyTtlMinutes);

            // 3. 만료 시간을 점수로 하여 active_sessions Set에 추가
            activeSessions.add(expiryTimestamp, userId);

            // 4. Redis Pub/Sub을 통해 해당 사용자에게 입장하라는 알림을 발행
            notificationService.sendAdmissionNotification(userId, accessKey);
        }

        // 4. 입장시킨 사용자 수만큼 활성 사용자 수를 원자적으로 증가시킵니다.
        long totalActiveUsers = redissonClient.getAtomicLong(ACTIVE_USERS_COUNT_KEY).addAndGet(admittedUsers.size());
        log.info("{}명 입장 완료. 현재 총 활성 사용자 수: {}", admittedUsers.size(), totalActiveUsers);
    }
}