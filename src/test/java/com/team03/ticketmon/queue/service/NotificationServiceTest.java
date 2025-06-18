package com.team03.ticketmon.queue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon._global.config.RedissonConfig;
import com.team03.ticketmon.queue.dto.AdmissionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationService가 Redis Pub/Sub으로 메시지를 올바르게 발행(Publish)하는지 검증하는 테스트 클래스.
 */
@ActiveProfiles("test")
@Testcontainers
@DataRedisTest
@Import({RedissonConfig.class, NotificationService.class, ObjectMapper.class})
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * @Container: 이 필드가 Testcontainer 임을 나타냅니다.
     * 테스트 클래스가 실행될 때 Docker를 이용해 Redis 컨테이너를 자동으로 실행시켜줍니다.
     */
    @Container
    public static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    /**
     * @DynamicPropertySource: Testcontainer가 실행된 후, 동적으로 할당된 IP와 포트 정보를
     * Spring의 Environment 속성으로 주입해주는 역할을 합니다.
     * 이를 통해 애플리케이션이 테스트용 임시 Redis 컨테이너에 연결될 수 있습니다.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> false);
    }

    @Test
    @DisplayName("입장 알림을 보내면, Redis Pub/Sub 토픽으로 AdmissionEvent 메시지가 발행된다.")
    void sendAdmissionNotification() throws InterruptedException, IOException {
        // given: 테스트용 데이터와 비동기 메시지 수신을 위한 환경 설정
        String userId = "user-test";
        String accessKey = "key-test";

        // CountDownLatch: 비동기 작업(메시지 수신)이 완료될 때까지 메인 스레드를 기다리게 하는 도구
        CountDownLatch latch = new CountDownLatch(1);
        // AtomicReference: 다른 스레드(리스너)에서 받은 메시지를 메인 스레드에서 안전하게 참조하기 위한 변수
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        // 테스트용 임시 구독자(Subscriber)를 생성하여 "admission-channel"을 구독
        RTopic topic = redissonClient.getTopic("admission-channel");
        topic.addListener(CharSequence.class, (channel, msg) -> {
            receivedMessage.set(msg.toString()); // 메시지 수신 시, AtomicReference에 저장
            latch.countDown();                   // Latch의 카운트를 감소시켜 대기 중인 스레드를 깨움
        });

        // when: 테스트 대상 메서드 호출 (메시지 발행)
        notificationService.sendAdmissionNotification(userId, accessKey);

        // then: 발행된 메시지가 구독자에게 성공적으로 도달했는지 검증
        // Latch가 0이 될 때까지 최대 5초간 대기. 시간 내에 메시지를 받으면 true 반환
        boolean isMessageReceived = latch.await(5, TimeUnit.SECONDS);
        assertThat(isMessageReceived).isTrue(); // 시간 내에 메시지를 받았는지 확인

        // 받은 JSON 메시지를 객체로 변환하여 내용이 정확한지 검증
        AdmissionEvent event = objectMapper.readValue(receivedMessage.get(), AdmissionEvent.class);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.accessKey()).isEqualTo(accessKey);
    }
}