package com.team03.ticketmon.seat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.seat.dto.SeatUpdateEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 좌석 상태 변경 이벤트 구독 서비스
 * - Redis Pub/Sub 채널에서 좌석 상태 변경 이벤트 수신
 * - 수신된 이벤트를 SeatPollingSessionManager에 전달하여 대기 중인 클라이언트들에게 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStatusEventSubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final SeatPollingSessionManager sessionManager;

    // 구독할 채널 패턴: seat:status:update:*
    private static final String CHANNEL_PATTERN = "seat:status:update:*";

    /**
     * 애플리케이션 시작 시 Redis 채널 구독 시작
     */
    @PostConstruct
    public void subscribeToSeatUpdateEvents() {
        try {
            // 패턴 기반 구독으로 모든 콘서트의 이벤트 수신
            RTopic topic = redissonClient.getTopic(CHANNEL_PATTERN);

            // 메시지 리스너 등록
            topic.addListener(CharSequence.class, this::handleSeatUpdateMessage);

            log.info("좌석 상태 이벤트 구독 시작: pattern={}", CHANNEL_PATTERN);

        } catch (Exception e) {
            log.error("좌석 상태 이벤트 구독 초기화 실패", e);
        }
    }

    /**
     * Redis에서 수신한 좌석 상태 변경 메시지 처리
     *
     * @param channel Redis 채널명 (예: seat:status:update:1)
     * @param message JSON 형태의 SeatUpdateEvent 메시지
     */
    private void handleSeatUpdateMessage(CharSequence channel, CharSequence message) {
        try {
            String channelName = channel.toString();
            String messageContent = message.toString();

            log.debug("좌석 상태 이벤트 수신: channel={}, message={}", channelName, messageContent);

            // JSON 메시지를 SeatUpdateEvent 객체로 역직렬화
            SeatUpdateEvent event = objectMapper.readValue(messageContent, SeatUpdateEvent.class);

            // 콘서트 ID 추출 및 검증
            Long concertId = event.concertId();
            if (concertId == null) {
                log.warn("콘서트 ID가 없는 이벤트 무시: message={}", messageContent);
                return;
            }

            // 채널명에서 콘서트 ID 검증 (보안 강화)
            if (!channelName.endsWith(":" + concertId)) {
                log.warn("채널명과 이벤트 콘서트 ID 불일치: channel={}, eventConcertId={}",
                        channelName, concertId);
                return;
            }

            // 세션 매니저에 이벤트 전달
            sessionManager.notifyWaitingSessions(event);

            log.info("좌석 상태 이벤트 처리 완료: concertId={}, seatId={}, status={}",
                    event.concertId(), event.seatId(), event.status());

        } catch (IOException e) {
            log.error("좌석 상태 이벤트 메시지 파싱 실패: channel={}, message={}",
                    channel, message, e);
        } catch (Exception e) {
            log.error("좌석 상태 이벤트 처리 중 예외 발생: channel={}, message={}",
                    channel, message, e);
        }
    }

    /**
     * 구독 상태 확인 (헬스체크용)
     *
     * @return 구독 활성화 여부
     */
    public boolean isSubscriptionActive() {
        try {
            RTopic topic = redissonClient.getTopic(CHANNEL_PATTERN);
            return topic.countListeners() > 0;
        } catch (Exception e) {
            log.warn("구독 상태 확인 실패", e);
            return false;
        }
    }

    /**
     * 구독 정보 조회 (디버깅용)
     *
     * @return 구독 패턴 및 상태 정보
     */
    public String getSubscriptionInfo() {
        try {
            RTopic topic = redissonClient.getTopic(CHANNEL_PATTERN);
            long listenerCount = topic.countListeners();

            return String.format("Pattern: %s, Listeners: %d, Active: %s",
                    CHANNEL_PATTERN, listenerCount, listenerCount > 0);
        } catch (Exception e) {
            log.warn("구독 정보 조회 실패", e);
            return "Pattern: " + CHANNEL_PATTERN + ", Status: UNKNOWN";
        }
    }
}