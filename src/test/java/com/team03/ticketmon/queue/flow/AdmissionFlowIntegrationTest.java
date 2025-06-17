package com.team03.ticketmon.queue.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team03.ticketmon.websocket.handler.CustomWebSocketHandler;
import com.team03.ticketmon.queue.dto.AdmissionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest
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

    @Test
    @DisplayName("입장 이벤트가 Redis에 발행되면, WebSocketHandler가 해당 사용자에게 입장 메시지를 전송한다.")
    void fullAdmissionFlowTest() throws Exception {
        // given
        String userId = "user-to-admit";
        String accessKey = "new-access-key";
        AdmissionEvent event = new AdmissionEvent(userId, accessKey);

        // 1. 테스트를 위한 가짜 WebSocket 세션을 생성합니다.
        WebSocketSession mockSession = mock(WebSocketSession.class);
        // 핸들러 내부 로직(session.isOpen())이 통과되도록 Mock 객체의 행동을 미리 지정(stubbing)합니다.
        when(mockSession.isOpen()).thenReturn(true);
        // 생성한 가짜 세션을 실제 핸들러에 등록하여, 'user-to-admit' 사용자가 연결된 것처럼 설정합니다.
        customWebSocketHandler.addSession(userId, mockSession);

        // 2. 테스트의 트리거(trigger)가 되는 입장 이벤트를 Redis 채널에 직접 발행합니다.
        String message = objectMapper.writeValueAsString(event);
        redissonClient.getTopic("admission-channel").publish(message);

        // when & then: 실행 및 결과 검증
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);

        // Awaitility를 사용해 비동기 작업(메시지 수신 및 처리)이 완료될 때까지 최대 10초간 기다립니다.
        // 최종적으로 customWebSocketHandler의 sendMessage 메서드가 호출되는지 검증합니다.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(customWebSocketHandler).sendMessage(eq(mockSession), messageCaptor.capture());
        });

        // 검증: 핸들러가 전송한 메시지의 내용이 정확한지 확인합니다.
        TextMessage sentMessage = messageCaptor.getValue();
        Map<String, Object> sentPayload = objectMapper.readValue(
                sentMessage.getPayload(),
                new TypeReference<>() {}
        );

        assertThat(sentPayload)
                .containsEntry("type", "ADMIT")
                .containsEntry("accessKey", accessKey);
    }
}