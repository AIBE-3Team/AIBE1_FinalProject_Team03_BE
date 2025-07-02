package com.team03.ticketmon.websocket.subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.queue.dto.AdmissionEvent;
import com.team03.ticketmon.websocket.handler.CustomWebSocketHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Redis의 'admission-channel' 토픽을 구독(Subscribe)하는 리스너
 * 메시지가 발행되면 이를 수신하여 WebSocket을 통해 특정 클라이언트에게 알림을 전달
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final CustomWebSocketHandler webSocketHandler;
    private static final String ADMISSION_TOPIC = RedisKeyGenerator.ADMISSION_TOPIC;

    /**
     * 빈(Bean)이 생성되고 의존성 주입이 완료된 후, 자동으로 Redis 토픽 구독을 시작
     */
    @PostConstruct // 빈이 생성된 후 자동으로 이 메서드를 실행
    public void subscribeToAdmissionTopic() {
        RTopic topic = redissonClient.getTopic(ADMISSION_TOPIC);

        // 메시지를 수신했을 때 실행될 리스너를 등록.
        topic.addListener(CharSequence.class, (channel, msg) -> {
//            log.debug("Redis 채널에서 메시지 수신. 채널: {}, 원본 메시지: {}", channel, msg);
            log.info("Redis 채널에서 메시지 수신. 채널: {}, 원본 메시지: {}", channel, msg);
            try {
                // 1. 수신된 JSON 메시지를 AdmissionEvent 객체로 역직렬화
                AdmissionEvent event = objectMapper.readValue(msg.toString(), AdmissionEvent.class);
                log.info("입장 알림 이벤트 수신 완료. 사용자: {}", event.userId());

                // 2. WebSocket 핸들러를 통해 해당 사용자에게 전송할 메시지(Payload)를 구성
                Map<String, Object> payload = Map.of(
                        "type", "ADMIT",
                        "accessKey", event.accessKey()
                );

                // 3. 구성된 메시지를 실제 클라이언트에게 전송
                webSocketHandler.sendMessageToUser(event.userId(), payload);

            } catch (IOException e) {
                // 메시지 파싱 또는 처리 실패는 데이터 형식 문제일 수 있으므로 ERROR 레벨로 기록
                log.error("수신된 Redis 메시지 처리 중 오류 발생!", e);
            }
        });

        log.info("Redis Pub/Sub 구독 시작. 채널: '{}'", ADMISSION_TOPIC);
    }
}