package com.team03.ticketmon.seat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import com.team03.ticketmon.seat.dto.SeatUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 좌석 상태 변경 이벤트 발행 서비스 (개선된 버전)
 * - 좌석 상태 변경 시 Redis Pub/Sub 채널에 이벤트 발행
 * - 실시간 좌석 상태 공유를 위한 핵심 컴포넌트
 * - 발행 통계 및 오류 처리 강화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStatusEventPublisher {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    // Redis 채널 패턴: seat:status:update:{concertId}
    private static final String CHANNEL_PREFIX = "seat:status:update:";

    // 발행 통계
    private final AtomicLong publishedEventCount = new AtomicLong(0);
    private final AtomicLong failedEventCount = new AtomicLong(0);

    /**
     * 좌석 상태 변경 이벤트 발행 (SeatStatus 객체 기반) - 개선된 버전
     *
     * @param seatStatus 변경된 좌석 상태 객체
     */
    public void publishSeatUpdate(SeatStatus seatStatus) {
        // ✅ 개선: 입력 유효성 검증
        if (seatStatus == null) {
            log.warn("SeatStatus가 null입니다. 이벤트 발행을 건너뜁니다.");
            failedEventCount.incrementAndGet();
            return;
        }

        // ✅ 개선: 필수 필드 검증
        if (!isValidSeatStatus(seatStatus)) {
            log.warn("유효하지 않은 SeatStatus입니다: {}", seatStatus);
            failedEventCount.incrementAndGet();
            return;
        }

        try {
            SeatUpdateEvent event = SeatUpdateEvent.from(seatStatus);
            publishEvent(event);

            log.debug("좌석 상태 이벤트 발행 성공 (SeatStatus): concertId={}, seatId={}, status={}",
                    seatStatus.getConcertId(), seatStatus.getSeatId(), seatStatus.getStatus());

        } catch (Exception e) {
            failedEventCount.incrementAndGet();
            log.error("좌석 상태 이벤트 발행 실패 (SeatStatus): concertId={}, seatId={}, status={}",
                    seatStatus.getConcertId(), seatStatus.getSeatId(), seatStatus.getStatus(), e);
        }
    }

    /**
     * 좌석 상태 변경 이벤트 발행 (개별 필드 기반) - 개선된 버전
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param status 좌석 상태
     * @param userId 사용자 ID (null 가능)
     * @param seatInfo 좌석 정보
     */
    public void publishSeatUpdate(Long concertId, Long seatId, SeatStatusEnum status,
                                  Long userId, String seatInfo) {

        // ✅ 개선: 입력 유효성 검증
        if (!isValidEventParameters(concertId, seatId, status, seatInfo)) {
            failedEventCount.incrementAndGet();
            return;
        }

        try {
            SeatUpdateEvent event = SeatUpdateEvent.of(concertId, seatId, status, userId, seatInfo);
            publishEvent(event);

            log.debug("좌석 상태 이벤트 발행 성공 (개별 필드): concertId={}, seatId={}, status={}",
                    concertId, seatId, status);

        } catch (Exception e) {
            failedEventCount.incrementAndGet();
            log.error("좌석 상태 이벤트 발행 실패 (개별 필드): concertId={}, seatId={}, status={}",
                    concertId, seatId, status, e);
        }
    }

    /**
     * 실제 이벤트 발행 로직 (개선된 버전)
     * - 채널명: seat:status:update:{concertId}
     * - 메시지: JSON 직렬화된 SeatUpdateEvent
     *
     * @param event 발행할 이벤트 객체
     */
    private void publishEvent(SeatUpdateEvent event) {
        try {
            String channelName = CHANNEL_PREFIX + event.concertId();
            RTopic topic = redissonClient.getTopic(channelName);

            // ✅ 개선: JSON 직렬화 예외 처리 강화
            String eventJson;
            try {
                eventJson = objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                failedEventCount.incrementAndGet();
                log.error("이벤트 JSON 직렬화 실패: event={}", event, e);
                return;
            }

            // ✅ 개선: 빈 JSON 검증
            if (eventJson == null || eventJson.trim().isEmpty()) {
                failedEventCount.incrementAndGet();
                log.error("직렬화된 JSON이 비어있습니다: event={}", event);
                return;
            }

            // Redis Pub/Sub으로 이벤트 발행
            long listenerCount = topic.publish(eventJson);
            publishedEventCount.incrementAndGet();

            log.info("좌석 상태 이벤트 발행 완료: channel={}, concertId={}, seatId={}, status={}, listeners={}",
                    channelName, event.concertId(), event.seatId(), event.status(), listenerCount);

            // ✅ 개선: 리스너가 없는 경우 경고
            if (listenerCount == 0) {
                log.warn("이벤트를 수신하는 리스너가 없습니다: channel={}, concertId={}",
                        channelName, event.concertId());
            }

        } catch (Exception e) {
            failedEventCount.incrementAndGet();
            log.error("이벤트 발행 중 예외 발생: concertId={}, seatId={}, status={}",
                    event.concertId(), event.seatId(), event.status(), e);
        }
    }

    /**
     * SeatStatus 객체 유효성 검증 (새로 추가)
     */
    private boolean isValidSeatStatus(SeatStatus seatStatus) {
        if (seatStatus.getConcertId() == null || seatStatus.getConcertId() <= 0) {
            log.warn("유효하지 않은 concertId: {}", seatStatus.getConcertId());
            return false;
        }

        if (seatStatus.getSeatId() == null || seatStatus.getSeatId() <= 0) {
            log.warn("유효하지 않은 seatId: {}", seatStatus.getSeatId());
            return false;
        }

        if (seatStatus.getStatus() == null) {
            log.warn("status가 null입니다");
            return false;
        }

        if (seatStatus.getSeatInfo() == null || seatStatus.getSeatInfo().trim().isEmpty()) {
            log.warn("seatInfo가 비어있습니다: {}", seatStatus.getSeatInfo());
            return false;
        }

        return true;
    }

    /**
     * 개별 필드 유효성 검증 (새로 추가)
     */
    private boolean isValidEventParameters(Long concertId, Long seatId, SeatStatusEnum status, String seatInfo) {
        if (concertId == null || concertId <= 0) {
            log.warn("유효하지 않은 concertId: {}", concertId);
            return false;
        }

        if (seatId == null || seatId <= 0) {
            log.warn("유효하지 않은 seatId: {}", seatId);
            return false;
        }

        if (status == null) {
            log.warn("status가 null입니다");
            return false;
        }

        if (seatInfo == null || seatInfo.trim().isEmpty()) {
            log.warn("seatInfo가 비어있습니다: {}", seatInfo);
            return false;
        }

        return true;
    }

    /**
     * 발행 통계 조회 (모니터링용)
     */
    public Map<String, Object> getPublisherStats() {
        long totalEvents = publishedEventCount.get() + failedEventCount.get();
        double successRate = totalEvents > 0 ? (double) publishedEventCount.get() / totalEvents * 100.0 : 0.0;

        return Map.of(
                "publishedEventCount", publishedEventCount.get(),
                "failedEventCount", failedEventCount.get(),
                "totalEventCount", totalEvents,
                "successRate", successRate,
                "channelPrefix", CHANNEL_PREFIX
        );
    }

    /**
     * 특정 콘서트의 채널명 조회 (디버깅용)
     */
    public String getChannelName(Long concertId) {
        if (concertId == null || concertId <= 0) {
            return null;
        }
        return CHANNEL_PREFIX + concertId;
    }

    /**
     * 통계 초기화 (테스트/관리용)
     */
    public void resetStats() {
        publishedEventCount.set(0);
        failedEventCount.set(0);
        log.info("이벤트 발행 통계가 초기화되었습니다");
    }

    /**
     * 테스트용 이벤트 발행 (개발/테스트 환경용)
     */
    @Value("${spring.profiles.active:}")
    private String activeProfile;

    public void publishTestEvent(Long concertId, Long seatId) {
        if (concertId == null || seatId == null) {
            log.warn("테스트 이벤트 발행 실패: concertId 또는 seatId가 null");
            return;
        }

        // 프로덕션 환경 체크
        if ("prod".equals(activeProfile) || "production".equals(activeProfile)) {
            log.error("테스트 이벤트는 프로덕션 환경에서 사용할 수 없습니다");
            return;
        }

        try {
            publishSeatUpdate(concertId, seatId, SeatStatusEnum.AVAILABLE, null, "TEST-" + seatId);
            log.info("테스트 이벤트 발행 완료: concertId={}, seatId={}", concertId, seatId);
        } catch (Exception e) {
            log.error("테스트 이벤트 발행 실패: concertId={}, seatId={}", concertId, seatId, e);
        }
    }
}