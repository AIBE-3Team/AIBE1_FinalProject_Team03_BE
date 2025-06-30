package com.team03.ticketmon.queue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.queue.dto.AdmissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub을 사용하여 입장 알림 메시지를 발행(Publish)하는 서비스.
 * 이 서비스를 통해 스케줄러와 웹소켓 핸들러 간의 의존성을 분리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private static final String ADMISSION_TOPIC = "admission-channel";

    /**
     * 특정 사용자에게 발급된 입장 허가 키를 담아 알림 이벤트를 발행.
     *
     * @param userId    알림을 받을 사용자 ID
     * @param accessKey 사용자에게 부여된 고유 입장 허가 키
     */
    public void sendAdmissionNotification(Long userId, String accessKey) {
        AdmissionEvent event = new AdmissionEvent(userId, accessKey);
        try {
            // 1. 이벤트 객체를 JSON 문자열로 직렬화
            String message = objectMapper.writeValueAsString(event);

//            log.debug("입장 알림 발행 준비. 채널: {}, 메시지: {}", ADMISSION_TOPIC, message);
            log.info("입장 알림 발행 준비. 채널: {}, 메시지: {}", ADMISSION_TOPIC, message);

            // 2. Redis의 특정 토픽(채널)을 가져옴
            RTopic topic = redissonClient.getTopic(ADMISSION_TOPIC);

            // 3. 해당 토픽을 구독(Subscribe)하고 있는 모든 클라이언트에게 메시지를 발행
            long receivers = topic.publish(message);

            // 애플리케이션의 중요 상태 변경이므로 INFO 레벨로 기록
            log.info("입장 알림 발행 완료. 사용자: {}, 채널: {}, 수신자 수: {}", userId, ADMISSION_TOPIC, receivers);
        } catch (JsonProcessingException e) {
            // 객체 직렬화는 프로그램 로직 오류일 가능성이 높으므로 ERROR 레벨로 기록
            log.error("AdmissionEvent 객체 JSON 직렬화 실패! userId: {}", userId, e);
        }
    }
}