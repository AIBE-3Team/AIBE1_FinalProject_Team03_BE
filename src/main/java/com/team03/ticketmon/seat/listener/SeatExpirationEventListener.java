// src/main/java/com/team03/ticketmon/seat/listener/SeatExpirationEventListener.java
package com.team03.ticketmon.seat.listener;

import com.team03.ticketmon.seat.service.SeatStatusService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redis Key Expiration Event 리스너
 * - Redis에서 TTL이 만료된 키에 대한 이벤트를 수신
 * - 좌석 관련 TTL 키 만료 시 자동으로 좌석 해제 처리
 *
 * 📋 동작 조건:
 * - Valkey notify-keyspace-events가 'Ex'로 설정되어야 함
 * - 키 패턴: seat:expire:{concertId}:{seatId}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatExpirationEventListener implements MessageListener {

    private final SeatStatusService seatStatusService;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    // TTL 키 패턴
    private static final String SEAT_EXPIRE_KEY_PATTERN = "seat:expire:*";
    private static final Pattern SEAT_KEY_REGEX = Pattern.compile("seat:expire:(\\d+):(\\d+)");

    /**
     * ✅ 초기화 시 Redis 만료 이벤트 구독 등록
     * - __keyevent@0__:expired 채널 구독
     * - 좌석 TTL 키 패턴만 필터링하여 처리
     */
    @PostConstruct
    public void init() {
        // Redis Key Expiration Event 구독
        // __keyevent@{db}__:expired 패턴으로 만료 이벤트 수신
        PatternTopic expiredKeysTopic = new PatternTopic("__keyevent@*__:expired");

        redisMessageListenerContainer.addMessageListener(this, expiredKeysTopic);

        log.info("Redis Key Expiration Event Listener 등록 완료 - 패턴: {}", SEAT_EXPIRE_KEY_PATTERN);
    }

    /**
     * ✅ Redis Key 만료 이벤트 처리
     * - 만료된 키가 좌석 TTL 키인지 확인
     * - 좌석 정보 추출 후 자동 해제 처리
     *
     * @param message Redis에서 수신한 메시지 (만료된 키 이름)
     * @param pattern 구독한 패턴
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 만료된 키 이름 추출
            String expiredKey = new String(message.getBody());
            String channel = new String(message.getChannel());

            log.debug("Redis Key 만료 이벤트 수신: channel={}, key={}", channel, expiredKey);

            // 좌석 TTL 키인지 확인
            if (expiredKey.startsWith("seat:expire:")) {
                handleSeatExpiration(expiredKey);
            } else {
                log.trace("좌석 관련 키가 아님 - 무시: {}", expiredKey);
            }

        } catch (Exception e) {
            log.error("Redis Key 만료 이벤트 처리 중 오류 발생", e);
        }
    }

    /**
     * ✅ 좌석 TTL 키 만료 처리
     * - 키에서 concertId, seatId 추출
     * - SeatStatusService.forceReleaseSeat() 호출하여 자동 해제
     *
     * @param expiredKey 만료된 좌석 TTL 키 (예: seat:expire:1:25)
     */
    private void handleSeatExpiration(String expiredKey) {
        try {
            // 키 패턴에서 concertId, seatId 추출
            Matcher matcher = SEAT_KEY_REGEX.matcher(expiredKey);

            if (!matcher.matches()) {
                log.warn("좌석 TTL 키 패턴이 올바르지 않음: {}", expiredKey);
                return;
            }

            Long concertId = Long.parseLong(matcher.group(1));
            Long seatId = Long.parseLong(matcher.group(2));

            log.info("좌석 TTL 만료 감지 - 자동 해제 시작: concertId={}, seatId={}, expiredKey={}",
                    concertId, seatId, expiredKey);

            // ✅ 좌석 자동 해제 처리
            seatStatusService.forceReleaseSeat(concertId, seatId);

            log.info("좌석 TTL 만료 자동 해제 완료: concertId={}, seatId={}", concertId, seatId);

        } catch (NumberFormatException e) {
            log.error("좌석 TTL 키에서 숫자 변환 실패: expiredKey={}", expiredKey, e);
        } catch (Exception e) {
            log.error("좌석 TTL 만료 처리 중 예외 발생: expiredKey={}", expiredKey, e);
        }
    }

    /**
     * ✅ 리스너 상태 확인용 메서드 (디버깅/모니터링용)
     *
     * @return 리스너 활성화 여부
     */
    public boolean isListenerActive() {
        return redisMessageListenerContainer.isRunning();
    }

    /**
     * ✅ 현재 구독 중인 토픽 정보 (디버깅용)
     *
     * @return 구독 패턴 정보
     */
    public String getSubscriptionInfo() {
        return String.format("Pattern: %s, Active: %s",
                SEAT_EXPIRE_KEY_PATTERN, isListenerActive());
    }
}