package com.team03.ticketmon.queue.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team03.ticketmon._global.config.RedissonConfig;
import com.team03.ticketmon.websocket.handler.CustomWebSocketHandler;
import com.team03.ticketmon.queue.dto.AdmissionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.websocket.subscriber.RedisMessageSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 사용자 입장 처리의 전체 흐름(End-to-End)을 검증하는 통합 테스트 클래스.
 * Redis에 입장 이벤트가 발행(Publish)되었을 때, 해당 이벤트가 구독자(Subscriber)를 통해 처리되어
 * 최종적으로 WebSocket 핸들러가 특정 사용자에게 메시지를 보내는 과정 전체를 테스트합니다.
 */
@ActiveProfiles("test")
@Testcontainers
@DataRedisTest
@Import({RedissonConfig.class, ObjectMapper.class, CustomWebSocketHandler.class, RedisMessageSubscriber.class})
class AdmissionFlowIntegrationTest {

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * {@code @MockitoSpyBean:} 실제 Spring Bean(CustomWebSocketHandler)을 가져오되,
     * 해당 객체의 일부 메서드 동작을 원하는 대로 제어(stubbing)하거나 호출 여부를 검증(verify)할 수 있게 합니다.
     * 실제 객체의 로직을 대부분 유지하면서 특정 상호작용만 테스트하고 싶을 때 유용합니다.
     */
    @MockitoSpyBean
    private CustomWebSocketHandler customWebSocketHandler;

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /**
     * [E2E 테스트] 사용자 입장 처리 전체 흐름을 검증합니다.
     *
     * 이 테스트의 목적은 Redis 'admission-channel'에 입장 이벤트가 발행(publish)되었을 때,
     * {@link RedisMessageSubscriber}가 이벤트를 수신하고,
     * 최종적으로 {@link CustomWebSocketHandler}가 특정 사용자에게 올바른 입장 메시지를 전송하는지
     * 전체 과정을 검증하는 것입니다.
     *
     * @throws Exception ObjectMapper, Awaitility 등에서 발생할 수 있는 예외
     */
    @Test
    @DisplayName("입장 이벤트가 Redis에 발행되면, WebSocketHandler가 해당 사용자에게 입장 메시지를 전송한다.")
    void fullAdmissionFlowTest() throws Exception {
        // GIVEN: 테스트를 위한 환경과 데이터를 설정합니다.
        String userId = "user-to-admit";
        String accessKey = "new-access-key";
        AdmissionEvent event = new AdmissionEvent(userId, accessKey);

        // Mockito를 사용해 가짜 WebSocket 세션을 생성합니다.
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.isOpen()).thenReturn(true);

        // Spy 객체인 핸들러에 'user-to-admit' 사용자가 현재 연결된 상태임을 수동으로 등록합니다.
        customWebSocketHandler.addSession(userId, mockSession);

        // WHEN: WaitingQueueScheduler의 동작을 모방하여 Redis 채널에 입장 이벤트를 직접 발행합니다.
        String message = objectMapper.writeValueAsString(event);
        redissonClient.getTopic("admission-channel").publish(message);

        // THEN: 결과를 검증합니다.

        // 1. ArgumentCaptor를 준비하여, 핸들러의 메소드로 전달된 'payload' Map 객체를 캡처합니다.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        // 2. Awaitility를 사용해 비동기(Pub/Sub) 작업을 최대 10초간 기다리며 검증합니다.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                // 3. Spy 객체인 customWebSocketHandler의 sendMessageToUser 메소드가
                //    올바른 userId와 함께 호출되었는지, 그리고 어떤 payload를 받았는지 캡처합니다.
                verify(customWebSocketHandler).sendMessageToUser(eq(userId), payloadCaptor.capture())
        );

        // 4. 캡처된 Map의 실제 내용을 검증합니다.
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload)
                .containsEntry("type", "ADMIT")
                .containsEntry("accessKey", accessKey);
    }
}