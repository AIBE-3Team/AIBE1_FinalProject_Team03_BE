package com.team03.ticketmon.queue.scheduler;

import com.team03.ticketmon.queue.service.NotificationService;
import com.team03.ticketmon.queue.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
    private final RedissonClient redissonClient;
    private final NotificationService notificationService;

    @Value("${app.max-active-users}")
    private long maxActiveUsers; // 시스템이 동시에 수용 가능한 최대 활성 사용자 수
    @Value("${app.access-key-ttl-minutes}")
    private long accessKeyTtlMinutes; // 발급된 입장 허가 키의 유효 시간 (분)

    // --- Redis 키 정의 ---
    /** 분산 락을 위한 키. 이 락을 획득한 인스턴스만이 스케줄러 로직을 실행. */
    private static final String ADMISSION_LOCK_KEY = "lock:admissionScheduler";
    /** 현재 활성 세션 정보를 저장하는 Sorted Set 키. Score는 세션 만료 시간(timestamp). */
    private static final String ACTIVE_SESSIONS_KEY = "active_sessions";
    /** 사용자별 입장 허가 키(Access Key)를 저장하는 String 키의 접두사. (예: accesskey:user-123) */
    private static final String ACCESS_KEY_PREFIX = "accesskey:";
    /** 현재 시스템의 총 활성 사용자 수를 저장하는 AtomicLong 키. */
    private static final String ACTIVE_USERS_COUNT_KEY = "active_users_count";

    // TODO: 현재는 단일 콘서트를 가정하여 ID를 1L로 고정했지만, 향후 여러 콘서트를 지원하려면 이 로직을 동적으로 변경해야함
    private static final long CONCERT_ID = 1L;


    /**
     * 10초마다 주기적으로 실행되어 대기열을 처리.
     * fixedDelay는 이전 작업이 성공적으로 끝난 후 10초를 기다리는 것을 의미.
     * 분산 락을 사용하여 여러 인스턴스 중 하나만 이 메서드를 실행하도록 보장.
     */
    @Scheduled(fixedDelay = 10000)
    public void execute() {

        // 분산 락 획득 시도
        RLock lock = redissonClient.getLock(ADMISSION_LOCK_KEY);
        try {
            // [분산 락 획득 시도]
            // waitTime(0): 락을 기다리지 않고 즉시 획득을 시도. (Fail-Fast)
            // leaseTime(10): 락을 획득하면 10초간 유지. 만약 이 스레드가 비정상 종료되어도 10초 후 락이 자동 해제되어 다른 인스턴스가 이어서 작업. (데드락 방지)
            boolean isLocked = lock.tryLock(0, 10, TimeUnit.SECONDS);

            // 락 획득에 실패하면, 다른 인스턴스가 이미 작업을 수행 중이라는 의미이므로 현재 작업을 종료
            if (!isLocked) {
                log.info("다른 인스턴스에서 스케줄러가 실행 중이므로, 현재 스케줄러는 건너뜁니다.");
                return;
            }

            log.info("===== 대기열 스케줄러 실행 시작 =====");

            // 현재 시스템의 활성 사용자 수를 Redis에서 조회
            RAtomicLong activeUsersCount = redissonClient.getAtomicLong(ACTIVE_USERS_COUNT_KEY);
            long currentActiveUsers = activeUsersCount.get();

            // 시스템 최대 수용 인원과 현재 활성 인원을 비교하여, 입장 가능한 빈자리(slot)를 계산.
            long availableSlots = maxActiveUsers - currentActiveUsers;


            //log.debug("활성 사용자 현황: {} / {} (빈자리: {})", currentActiveUsers, maxActiveUsers, availableSlots);
            log.info("활성 사용자 현황: {} / {} (빈자리: {})", currentActiveUsers, maxActiveUsers, availableSlots);

            if (availableSlots <= 0) {
                log.info("===== 입장 가능한 자리가 없습니다. 스케줄러 작업을 종료 =====");
                return;
            }

            // WaitingQueueService를 통해 빈자리 수만큼 사용자를 원자적으로 추출.
            List<String> admittedUsers = waitingQueueService.poll(CONCERT_ID, (int) availableSlots);

            if (admittedUsers.isEmpty()) {
                log.info("===== 새로 입장할 대기 인원이 없습니다. 스케줄러 작업을 종료 =====");
                return;
            }

            // 추출된 사용자들에게 입장을 허가하는 후속 작업을 수행
            grantAccessToUsers(admittedUsers);

            log.info("===== 대기열 스케줄러 실행 종료 =====");

        } catch (InterruptedException e) {
            // 스레드가 중단 신호를 받으면, 현재 스레드의 중단 상태를 다시 설정하여 상위 코드가 인지할 수 있도록 함.
            Thread.currentThread().interrupt();
            log.error("분산 락을 획득하는 동안 인터럽트 발생", e);
        } finally {
            // 현재 스레드가 락을 점유하고 있는 경우에만 해제를 시도하여, 다른 스레드가 획득한 락을 해제하는 실수를 방지.
            if  (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 입장 허가된 사용자들에게 고유 키를 발급하고, 알림을 전송한 후, 활성 사용자 관련 상태를 업데이트.
     * 이 모든 과정은 하나의 트랜잭션처럼 동작해야 함. (현재는 개별 연산으로 구성)
     *
     * @param admittedUsers 입장 허가된 사용자 ID 리스트
     */
    private void grantAccessToUsers(List<String> admittedUsers) {
        log.info("{}명의 신규 사용자 입장 처리 시작: {}", admittedUsers.size(), admittedUsers);

        // 모든 신규 입장자에게 동일한 만료 시간을 적용하기 위해 현재 시간을 기준으로 만료 타임스탬프를 계산.
        RScoredSortedSet<String> activeSessions = redissonClient.getScoredSortedSet(ACTIVE_SESSIONS_KEY);
        long expiryTimestamp = System.currentTimeMillis() + accessKeyTtlMinutes * 60 * 1000;

        for (String userId : admittedUsers) {
            // 1. 사용자별로 예측 불가능한 고유 AccessKey를 발급하여, 다른 사용자가 키를 추측하여 부정 사용하는 것을 방지.
            String accessKey = UUID.randomUUID().toString();
            RBucket<String> accessKeyBucket = redissonClient.getBucket(ACCESS_KEY_PREFIX + userId);

            // 2. Redis에 AccessKey를 TTL(Time-To-Live)과 함께 저장. 사용자가 이 키를 사용하여 실제 서비스에 접근하면, 서비스는 이 키의 유효성을 검증.
            accessKeyBucket.set(accessKey, Duration.ofMinutes(accessKeyTtlMinutes));
            //log.debug("사용자 '{}'에게 접근 키 발급 및 저장 완료 (유효 시간: {}분)", userId, accessKeyTtlMinutes);
            log.info("사용자 '{}'에게 접근 키 발급 및 저장 완료 (유효 시간: {}분)", userId, accessKeyTtlMinutes);

            // 3. 만료 시간을 점수(score)로 하여 'active_sessions' Set에 추가.
            //    이는 나중에 CleanupScheduler가 만료된 세션을 효율적으로 찾아 정리하는 데 사용.
            activeSessions.add(expiryTimestamp, userId);

            // 4. Redis Pub/Sub을 통해 해당 사용자에게 입장하라는 알림을 발행
            notificationService.sendAdmissionNotification(userId, accessKey);
        }

        // 5. 입장시킨 사용자 수만큼 활성 사용자 수 원자적으로 증가.
        //    루프 안에서 1씩 여러 번 증가시키는 것보다, 루프 종료 후 한 번에 더하는 것이 Redis와의 통신 횟수를 줄여 더 효율적.
        long totalActiveUsers = redissonClient.getAtomicLong(ACTIVE_USERS_COUNT_KEY).addAndGet(admittedUsers.size());
        log.info("{}명 입장 완료. 현재 총 활성 사용자 수: {}", admittedUsers.size(), totalActiveUsers);
    }
}