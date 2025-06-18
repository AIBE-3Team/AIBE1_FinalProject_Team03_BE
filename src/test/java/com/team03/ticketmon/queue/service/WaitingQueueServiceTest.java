package com.team03.ticketmon.queue.service;

import com.team03.ticketmon._global.config.RedissonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WaitingQueueService의 핵심 기능(대기열 추가, 조회, 추출)을 검증하는 테스트 클래스.
 * Redis의 Sorted Set을 사용하는 로직이 정확히 동작하는지 확인합니다.
 */
@ActiveProfiles("test") // 테스트용 프로필 사용
@Testcontainers
@DataRedisTest
@Import({RedissonConfig.class, WaitingQueueService.class})
class WaitingQueueServiceTest {

    @Autowired
    private WaitingQueueService waitingQueueService;
    @Autowired
    private RedissonClient redissonClient;

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);


    /**
     * @DynamicPropertySource는 Testcontainers의 컨테이너가 실행된 후,
     * 스프링의 ApplicationContext가 로드되기 전에 호출됩니다. (없으면 컨테이너 실행전 로드라 오류)
     * 이 메서드를 통해 동적으로 생성된 컨테이너의 주소와 포트를
     * 스프링 설정(Environment)에 주입할 수 있습니다.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> false);
    }

    /**
     * 각 테스트가 실행되기 전, Redis 데이터를 모두 삭제하여 테스트 간의 완벽한 독립성을 보장합니다.
     * (@AfterEach 대신 @BeforeEach를 사용하면, 테스트 실패 시에도 다음 테스트는 깨끗한 환경에서 시작할 수 있습니다.)
     */
    @BeforeEach
    void setUp() {
        // 현재 Redis 데이터베이스의 모든 키를 삭제합니다.
        redissonClient.getKeys().flushdb();
    }

    @Test
    @DisplayName("여러 사용자가 순차적으로 진입하면 각자의 순번을 정확히 반환한다.")
    void applyAndGetRank_multipleUsers() {
        // given, when: 3명의 사용자가 순서대로 대기열에 진입
        Long rank1 = waitingQueueService.apply(1L, "user-1");
        Long rank2 = waitingQueueService.apply(1L, "user-2");
        Long rank3 = waitingQueueService.apply(1L, "user-3");

        // then: 각자 1, 2, 3등의 순번을 부여받는다.
        assertThat(rank1).isEqualTo(1L);
        assertThat(rank2).isEqualTo(2L);
        assertThat(rank3).isEqualTo(3L);
    }

    @Test
    @DisplayName("이미 대기열에 있는 사용자가 다시 진입을 시도해도 순번은 변하지 않는다 (멱등성).")
    void applyAndGetRank_idempotency() throws InterruptedException {
        // given: user-1이 먼저 진입하고, 잠시 후 user-2가 진입
        Long initialRankOfUser1 = waitingQueueService.apply(1L, "user-1");
        Thread.sleep(10); // 점수(timestamp)를 다르게 하기 위한 지연
        waitingQueueService.apply(1L, "user-2");

        // when: user-1이 다시 진입을 시도
        Long updatedRankOfUser1 = waitingQueueService.apply(1L, "user-1");

        // then: user-1의 순번은 처음 부여받은 1등에서 변하지 않는다.
        assertThat(initialRankOfUser1).isEqualTo(1L);
        assertThat(updatedRankOfUser1).isEqualTo(initialRankOfUser1);
    }

    @Test
    @DisplayName("서로 다른 콘서트의 대기열은 서로 영향을 주지 않는다.")
    void apply_shouldManageQueuesSeparately_forDifferentConcerts() {
        // given: 두 개의 다른 콘서트 ID
        long concertIdA = 1L;
        long concertIdB = 2L;

        // when: 각 콘서트에 사용자들이 신청
        Long rankA1 = waitingQueueService.apply(concertIdA, "user-1");
        Long rankB1 = waitingQueueService.apply(concertIdB, "user-A");
        Long rankA2 = waitingQueueService.apply(concertIdA, "user-2");

        // then: 각 콘서트 대기열은 독립적으로 순번을 계산한다.
        assertThat(rankA1).isEqualTo(1L);
        assertThat(rankB1).isEqualTo(1L);
        assertThat(rankA2).isEqualTo(2L);
    }

    @Test
    @DisplayName("대기열에서 N명을 추출(poll)하면, 가장 오래 기다린 N명이 순서대로 반환되고 대기열에서 제거된다.")
    void poll() throws InterruptedException {
        // given: 3명의 사용자가 순서대로 대기열에 진입
        long concertId = 1L;
        waitingQueueService.apply(concertId, "user-1");
        Thread.sleep(10);
        waitingQueueService.apply(concertId, "user-2");
        Thread.sleep(10);
        waitingQueueService.apply(concertId, "user-3");

        // when: 가장 오래 기다린 2명을 추출
        List<String> polledUsers = waitingQueueService.poll(concertId, 2);

        // then:
        // 1. 요청한 2명이 반환되었는가?
        assertThat(polledUsers).hasSize(2);
        // 2. 가장 먼저 들어온 순서("user-1", "user-2")대로 반환되었는가?
        assertThat(polledUsers).containsExactly("user-1", "user-2");
        // 3. 대기열에 남은 인원은 1명인가?
        assertThat(waitingQueueService.getWaitingCount(concertId)).isEqualTo(1L);
        // 4. 대기열에 남은 user-3의 순번은 1등이 되었는가?
        assertThat(waitingQueueService.apply(concertId, "user-3")).isEqualTo(1L);
    }
}