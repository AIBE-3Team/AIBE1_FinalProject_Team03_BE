package com.team03.ticketmon.seat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.seat.dto.SeatUpdateEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.PatternMessageListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 좌석 상태 변경 이벤트 구독 서비스 (개선된 버전)
 * - Redis Pub/Sub 채널에서 좌석 상태 변경 이벤트 수신
 * - 수신된 이벤트를 SeatPollingSessionManager에 전달하여 대기 중인 클라이언트들에게 알림
 * - 연결 안정성 및 오류 처리 강화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStatusEventSubscriber implements PatternMessageListener<CharSequence> {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final SeatPollingSessionManager sessionManager;

    // 구독할 채널 패턴: seat:status:update:*
    private static final String CHANNEL_PATTERN = "seat:status:update:*";

    // 상태 관리
    private final AtomicBoolean isSubscribed = new AtomicBoolean(false);
    private final AtomicLong processedEventCount = new AtomicLong(0);
    private final AtomicLong errorEventCount = new AtomicLong(0);

    private RPatternTopic patternTopic;
    private int listenerId = -1;

    /**
     * 애플리케이션 시작 시 Redis 채널 구독 시작 (개선된 버전)
     */
    @PostConstruct
    public void subscribeToSeatUpdateEvents() {
        try {
            // ✅ 개선: RPatternTopic 사용으로 패턴 매칭 최적화
            patternTopic = redissonClient.getPatternTopic(CHANNEL_PATTERN);

            // ✅ 개선: this를 PatternMessageListener로 직접 사용
            listenerId = patternTopic.addListener(CharSequence.class, this);

            isSubscribed.set(true);

            log.info("좌석 상태 이벤트 구독 시작: pattern={}, listenerId={}",
                    CHANNEL_PATTERN, listenerId);

        } catch (Exception e) {
            log.error("좌석 상태 이벤트 구독 초기화 실패: pattern={}", CHANNEL_PATTERN, e);
            isSubscribed.set(false);
        }
    }

    /**
     * 애플리케이션 종료 시 구독 해제
     */
    @PreDestroy
    public void unsubscribeFromSeatUpdateEvents() {
        try {
            if (patternTopic != null && listenerId != -1) {
                patternTopic.removeListener(listenerId);
                log.info("좌석 상태 이벤트 구독 해제: pattern={}, listenerId={}",
                        CHANNEL_PATTERN, listenerId);
            }
            isSubscribed.set(false);
        } catch (Exception e) {
            log.warn("좌석 상태 이벤트 구독 해제 중 오류", e);
        }
    }

    /**
     * PatternMessageListener 인터페이스 구현 - Redis에서 수신한 메시지 처리
     *
     * @param pattern 매칭된 패턴
     * @param channel Redis 채널명 (예: seat:status:update:1)
     * @param message JSON 형태의 SeatUpdateEvent 메시지
     */
    @Override
    public void onMessage(CharSequence pattern, CharSequence channel, CharSequence message) {
        handleSeatUpdateMessage(channel, message);
    }

    /**
     * Redis에서 수신한 좌석 상태 변경 메시지 처리 (개선된 버전)
     *
     * @param channel Redis 채널명 (예: seat:status:update:1)
     * @param message JSON 형태의 SeatUpdateEvent 메시지
     */
    private void handleSeatUpdateMessage(CharSequence channel, CharSequence message) {
        String channelName = channel.toString();
        String messageContent = message.toString();

        try {
            log.debug("좌석 상태 이벤트 수신: channel={}, message={}", channelName, messageContent);

            // ✅ 개선: 빈 메시지 검증
            if (messageContent == null || messageContent.trim().isEmpty()) {
                log.warn("빈 메시지 수신: channel={}", channelName);
                errorEventCount.incrementAndGet();
                return;
            }

            // JSON 메시지를 SeatUpdateEvent 객체로 역직렬화
            SeatUpdateEvent event = objectMapper.readValue(messageContent, SeatUpdateEvent.class);

            // ✅ 개선: 이벤트 데이터 유효성 검증 강화
            if (!isValidEvent(event)) {
                log.warn("유효하지 않은 이벤트 무시: channel={}, event={}", channelName, event);
                errorEventCount.incrementAndGet();
                return;
            }

            // 콘서트 ID 추출 및 검증
            Long concertId = event.concertId();

            // ✅ 개선: 채널명과 이벤트 콘서트 ID 일치성 검증
            if (!isChannelConcertIdMatch(channelName, concertId)) {
                log.warn("채널명과 이벤트 콘서트 ID 불일치: channel={}, eventConcertId={}",
                        channelName, concertId);
                errorEventCount.incrementAndGet();
                return;
            }

            // ✅ 핵심: 세션 매니저에 이벤트 전달
            sessionManager.notifyWaitingSessions(event);

            // 성공 카운터 증가
            processedEventCount.incrementAndGet();

            log.info("좌석 상태 이벤트 처리 완료: concertId={}, seatId={}, status={}, processedTotal={}",
                    concertId, event.seatId(), event.status(), processedEventCount.get());

        } catch (Exception e) {
            errorEventCount.incrementAndGet();
            log.error("좌석 상태 이벤트 처리 실패: channel={}, message={}, errorTotal={}",
                    channelName, messageContent, errorEventCount.get(), e);
        }
    }

    /**
     * 이벤트 데이터 유효성 검증 (새로 추가)
     *
     * @param event 검증할 이벤트
     * @return 유효한 경우 true
     */
    private boolean isValidEvent(SeatUpdateEvent event) {
        if (event == null) {
            return false;
        }

        // 필수 필드 검증
        if (event.concertId() == null || event.seatId() == null || event.status() == null) {
            log.warn("필수 필드 누락: concertId={}, seatId={}, status={}",
                    event.concertId(), event.seatId(), event.status());
            return false;
        }

        // 콘서트 ID와 좌석 ID는 양수여야 함
        if (event.concertId() <= 0 || event.seatId() <= 0) {
            log.warn("잘못된 ID 값: concertId={}, seatId={}",
                    event.concertId(), event.seatId());
            return false;
        }

        // seatInfo 검증 (null이 아니고 비어있지 않아야 함)
        if (event.seatInfo() == null || event.seatInfo().trim().isEmpty()) {
            log.warn("좌석 정보 누락: seatInfo={}", event.seatInfo());
            return false;
        }

        return true;
    }

    /**
     * 채널명과 콘서트 ID 일치성 검증 (개선된 버전)
     *
     * @param channelName 채널명
     * @param concertId 이벤트의 콘서트 ID
     * @return 일치하는 경우 true
     */
    private boolean isChannelConcertIdMatch(String channelName, Long concertId) {
        try {
            // 채널명에서 콘서트 ID 추출: seat:status:update:{concertId}
            String expectedChannelName = "seat:status:update:" + concertId;
            return expectedChannelName.equals(channelName);
        } catch (Exception e) {
            log.warn("채널명 파싱 오류: channel={}, concertId={}", channelName, concertId, e);
            return false;
        }
    }

    /**
     * 구독 상태 확인 (헬스체크용)
     */
    public boolean isSubscribed() {
        return isSubscribed.get();
    }

    /**
     * 처리 통계 조회 (모니터링용)
     */
    public Map<String, Object> getSubscriberStats() {
        return Map.of(
                "isSubscribed", isSubscribed.get(),
                "channelPattern", CHANNEL_PATTERN,
                "listenerId", listenerId,
                "processedEventCount", processedEventCount.get(),
                "errorEventCount", errorEventCount.get(),
                "successRate", calculateSuccessRate()
        );
    }

    /**
     * 성공률 계산
     */
    private double calculateSuccessRate() {
        long total = processedEventCount.get() + errorEventCount.get();
        if (total == 0) return 0.0;
        return (double) processedEventCount.get() / total * 100.0;
    }

    /**
     * 구독 재시작 (장애 복구용)
     */
    public boolean restartSubscription() {
        try {
            log.info("좌석 상태 이벤트 구독 재시작 시도");

            // 기존 구독 해제
            unsubscribeFromSeatUpdateEvents();

            // 잠깐 대기
            Thread.sleep(1000);

            // 새로 구독
            subscribeToSeatUpdateEvents();

            return isSubscribed.get();
        } catch (Exception e) {
            log.error("좌석 상태 이벤트 구독 재시작 실패", e);
            return false;
        }
    }
}