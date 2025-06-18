package com.team03.ticketmon.queue.scheduler;

import com.team03.ticketmon._global.config.RedissonConfig;
import com.team03.ticketmon.queue.service.NotificationService;
import com.team03.ticketmon.queue.service.WaitingQueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * WaitingQueueScheduler의 핵심 로직이 올바르게 동작하는지 검증하는 통합 테스트.
 * 실제 스케줄링 시간(fixedDelay)에 의존하지 않고, scheduler.execute() 메서드를 직접 호출하여
 * 실행 전후의 Redis 상태 변화를 확인합니다.
 */
@ActiveProfiles("test")
@Testcontainers
@DataRedisTest
@Import({RedissonConfig.class, WaitingQueueService.class, WaitingQueueScheduler.class})
class WaitingQueueSchedulerIntegrationTest {

    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private WaitingQueueScheduler waitingQueueScheduler;
    @Autowired
    private WaitingQueueService waitingQueueService;
    @Autowired
    private RedissonClient redissonClient;

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> false);
    }

    /**
     * 각 테스트 실행 후, Redis의 모든 데이터를 삭제하여 테스트 간의 독립성을 보장합니다.
     */
    @AfterEach
    void tearDown() {
        // 각 테스트가 서로에게 영향을 주지 않도록, 테스트 실행 후 Redis 데이터를 모두 삭제합니다.
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("스케줄러가 실행되면, 대기열의 사용자를 입장시키고, AccessKey를 발급하며, 활성 사용자 수를 증가시킨다.")
    void execute_shouldAdmitUsers_and_updateStateInRedis() throws InterruptedException {
        // given: 3명의 사용자가 대기열에 있고, 2개의 빈 자리가 있는 상황
        long concertId = 1L;
        waitingQueueService.apply(concertId, "user-1");
        waitingQueueService.apply(concertId, "user-2");
        waitingQueueService.apply(concertId, "user-3");

        RAtomicLong activeUsersCount = redissonClient.getAtomicLong("active_users_count");
        activeUsersCount.set(498); // 최대 500명 중 498명 -> 2자리 남음

        // when: 스케줄러 로직 실행
        waitingQueueScheduler.execute();

        // then: 로직 실행 후 Redis의 상태가 올바르게 변경되었는지 검증
        // 비동기(Pub/Sub) 처리 시간을 고려하여 Awaitility로 최대 5초 대기
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            // 1. 활성 사용자 수가 2명 증가하여 500명이 되었는가?
            assertThat(redissonClient.getAtomicLong("active_users_count").get()).isEqualTo(500L);

            // 2. 대기열에는 1명(user-3)만 남아있는가?
            assertThat(waitingQueueService.getWaitingCount(concertId)).isEqualTo(1L);

            // 3. 입장 처리된 사용자(user-1, user-2)에게 AccessKey가 발급되었는가?
            RBucket<String> accessKey1 = redissonClient.getBucket("accesskey:user-1");
            RBucket<String> accessKey2 = redissonClient.getBucket("accesskey:user-2");
            assertThat(accessKey1.isExists()).isTrue();
            assertThat(accessKey2.isExists()).isTrue();

            // 4. 발급된 AccessKey에 유효시간(TTL)이 올바르게 설정되었는가? (10분 이하)
            assertThat(accessKey1.remainTimeToLive()).isPositive().isLessThanOrEqualTo(TimeUnit.MINUTES.toMillis(10));
        });
    }

    @Test
    @DisplayName("활성 사용자 수가 최대치 이상일 때, 스케줄러는 아무 작업도 하지 않아야 한다.")
    void execute_shouldDoNothing_whenNoSlotIsAvailable() throws InterruptedException {
        // given: 대기열에 사용자가 있지만, 활성 사용자 수가 꽉 찬 상황
        long concertId = 1L;
        waitingQueueService.apply(concertId, "user-1");

        RAtomicLong activeUsersCount = redissonClient.getAtomicLong("active_users_count");
        activeUsersCount.set(500); // 빈자리 없음

        // when: 스케줄러 로직 실행
        waitingQueueScheduler.execute();

        // then: Redis의 상태에 아무 변화가 없어야 함
        // 1. 활성 사용자 수는 그대로 500명이어야 한다.
        assertThat(activeUsersCount.get()).isEqualTo(500L);
        // 2. 대기열 인원도 그대로 1명이어야 한다.
        assertThat(waitingQueueService.getWaitingCount(concertId)).isEqualTo(1L);
        // 3. 대기중인 사용자에게 AccessKey가 발급되지 않았어야 한다.
        assertThat(redissonClient.getBucket("accesskey:user-1").isExists()).isFalse();
    }
}