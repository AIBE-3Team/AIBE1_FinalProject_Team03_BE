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
import org.springframework.stereotype.Service;

/**
 * 좌석 상태 변경 이벤트 발행 서비스
 * - 좌석 상태 변경 시 Redis Pub/Sub 채널에 이벤트 발행
 * - 실시간 좌석 상태 공유를 위한 핵심 컴포넌트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStatusEventPublisher {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    // Redis 채널 패턴: seat:status:update:{concertId}
    private static final String CHANNEL_PREFIX = "seat:status:update:";

    /**
     * 좌석 상태 변경 이벤트 발행 (SeatStatus 객체 기반)
     *
     * @param seatStatus 변경된 좌석 상태 객체
     */
    public void publishSeatUpdate(SeatStatus seatStatus) {
        try {
            SeatUpdateEvent event = SeatUpdateEvent.from(seatStatus);
            publishEvent(event);

        } catch (Exception e) {
            log.error("좌석 상태 이벤트 발행 실패 (SeatStatus): concertId={}, seatId={}, status={}",
                    seatStatus.getConcertId(), seatStatus.getSeatId(), seatStatus.getStatus(), e);
        }
    }

    /**
     * 좌석 상태 변경 이벤트 발행 (개별 필드 기반)
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param status 좌석 상태
     * @param userId 사용자 ID (null 가능)
     * @param seatInfo 좌석 정보
     */
    public void publishSeatUpdate(Long concertId, Long seatId, SeatStatusEnum status,
                                  Long userId, String seatInfo) {
        try {
            SeatUpdateEvent event = SeatUpdateEvent.of(concertId, seatId, status, userId, seatInfo);
            publishEvent(event);

        } catch (Exception e) {
            log.error("좌석 상태 이벤트 발행 실패 (개별 필드): concertId={}, seatId={}, status={}",
                    concertId, seatId, status, e);
        }
    }

    /**
     * 실제 이벤트 발행 로직
     * - 채널명: seat:status:update:{concertId}
     * - 메시지: JSON 직렬화된 SeatUpdateEvent
     *
     * @param event 발행할 이벤트 객체
     */
    private void publishEvent(SeatUpdateEvent event) {
        try {
            String channelName = CHANNEL_PREFIX + event.concertId();
            RTopic topic = redissonClient.getTopic(channelName);

            // JSON 직렬화
            String eventJson = objectMapper.writeValueAsString(event);

            // 비동기 발행 (논블로킹)
            topic.publishAsync(eventJson);

            log.debug("좌석 상태 이벤트 발행 완료: channel={}, seatId={}, status={}",
                    channelName, event.seatId(), event.status());

        } catch (JsonProcessingException e) {
            log.error("좌석 이벤트 JSON 직렬화 실패: concertId={}, seatId={}",
                    event.concertId(), event.seatId(), e);
        } catch (Exception e) {
            log.error("Redis 토픽 발행 실패: concertId={}, seatId={}",
                    event.concertId(), event.seatId(), e);
        }
    }

    /**
     * 특정 콘서트의 채널명 반환 (테스트 및 디버깅용)
     *
     * @param concertId 콘서트 ID
     * @return Redis 채널명
     */
    public String getChannelName(Long concertId) {
        return CHANNEL_PREFIX + concertId;
    }

    /**
     * 이벤트 발행 상태 확인 (헬스체크용)
     *
     * @param concertId 콘서트 ID
     * @return 해당 채널의 구독자 수
     */
    public long getSubscriberCount(Long concertId) {
        try {
            String channelName = CHANNEL_PREFIX + concertId;
            RTopic topic = redissonClient.getTopic(channelName);
            return topic.countSubscribers();
        } catch (Exception e) {
            log.warn("구독자 수 조회 실패: concertId={}", concertId, e);
            return 0;
        }
    }
}