package com.team03.ticketmon.seat.domain;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.LocalDateTime;

/**
 * Redis Hash 기반 좌석 상태 엔티티
 * - 키: seat:status:{concertId}
 * - Hash 필드: seatId를 키로 하는 Hash 구조
 */
@Getter
@Builder
@RedisHash(value = "seat:status", timeToLive = 3600) // 1시간 TTL
public class SeatStatus {

    @Id
    private String id; // concertId-seatId 형태로 구성

    private Long concertId;      // 콘서트 ID
    private Long seatId;         // 좌석 ID
    private SeatStatusEnum status; // 좌석 상태
    private Long userId;         // 선점한 사용자 ID (선점 시에만)
    private LocalDateTime reservedAt; // 선점 시간
    private LocalDateTime expiresAt;  // 선점 만료 시간
    private String seatInfo;     // 좌석 정보 (A-1, B-15 등)

    /**
     * 좌석 상태 enum
     */
    public enum SeatStatusEnum {
        AVAILABLE,    // 예매 가능
        RESERVED,     // 임시 선점 (5분)
        BOOKED,       // 예매 완료
        UNAVAILABLE   // 예매 불가
    }

    /**
     * 좌석이 선점 가능한지 확인
     */
    public boolean isAvailable() {
        return status == SeatStatusEnum.AVAILABLE;
    }

    /**
     * 좌석이 현재 선점 중인지 확인
     */
    public boolean isReserved() {
        return status == SeatStatusEnum.RESERVED;
    }

    /**
     * 좌석 선점이 만료되었는지 확인
     */
    public boolean isExpired() {
        return isReserved() && expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}