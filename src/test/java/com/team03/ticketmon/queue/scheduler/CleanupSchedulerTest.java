package com.team03.ticketmon.queue.scheduler;

import com.team03.ticketmon._global.config.RedissonConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Testcontainers
@DataRedisTest
@Import({RedissonConfig.class, CleanupScheduler.class})
class CleanupSchedulerTest {

    @Autowired
    private CleanupScheduler cleanupScheduler;

    @Autowired
    private RedissonClient redissonClient;

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("정리 스케줄러가 실행되면, 만료된 세션을 감지하여 활성 사용자 수를 감소시킨다.")
    void cleanupExpiredSessions() {
        // given
        // 1. 초기 활성 사용자 수를 5로 설정
        RAtomicLong activeUsersCount = redissonClient.getAtomicLong("active_users_count");
        activeUsersCount.set(5);

        // 2. active_sessions Set에 테스트 데이터 추가
        RScoredSortedSet<String> activeSessions = redissonClient.getScoredSortedSet("active_sessions");
        long now = System.currentTimeMillis();
        // - 만료된 사용자 2명 (과거 시간)
        activeSessions.add(now - 10000, "expired-user-1");
        activeSessions.add(now - 5000, "expired-user-2");
        // - 아직 유효한 사용자 3명 (미래 시간)
        activeSessions.add(now + 60000, "active-user-1");
        activeSessions.add(now + 70000, "active-user-2");
        activeSessions.add(now + 80000, "active-user-3");

        // when
        cleanupScheduler.cleanupExpiredSessions();

        // then
        // 1. 활성 사용자 수가 2명 감소하여 3명이 되었는가?
        assertThat(activeUsersCount.get()).isEqualTo(3);
        // 2. active_sessions Set에는 유효한 3명만 남아있는가?
        assertThat(activeSessions.size()).isEqualTo(3);
        // 3. 만료된 사용자가 정말로 삭제되었는가?
        assertThat(activeSessions.contains("expired-user-1")).isFalse();
    }
}