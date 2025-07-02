package com.team03.ticketmon.seat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 좌석 동기화 결과 DTO
 *
 * 단일 콘서트의 Redis-DB 동기화 작업 결과를 담는 record 클래스
 *
 * 기능:
 * - 동기화 성공/실패 여부
 * - 처리된 좌석 수 통계
 * - 불일치 데이터 상세 정보
 * - 동기화 소요 시간
 */
@Getter
@Builder
public class SeatSyncResult {

    /** 동기화 대상 콘서트 ID */
    private final Long concertId;

    /** 동기화 시작 시간 */
    private final LocalDateTime syncStartTime;

    /** 동기화 종료 시간 */
    private final LocalDateTime syncEndTime;

    /** DB 총 좌석 수 */
    private final int totalDbSeats;

    /** Redis 총 좌석 수 */
    private final int totalRedisSeats;

    /** 상태 불일치 좌석 수 */
    private final int inconsistentSeats;

    /** Redis에 누락된 좌석 수 */
    private final int missingInRedis;

    /** Redis에만 존재하는 불필요한 좌석 수 */
    private final int extraInRedis;

    /** 수정된 좌석 수 */
    private final int fixedSeats;

    /** 동기화 성공 여부 */
    private final boolean success;

    /** 오류 메시지 (실패 시) */
    private final String errorMessage;

    /**
     * 동기화 소요 시간 계산
     *
     * @return 소요 시간 (Duration)
     */
    public Duration getDuration() {
        if (syncStartTime == null || syncEndTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(syncStartTime, syncEndTime);
    }

    /**
     * 동기화 결과 요약 메시지 생성
     *
     * @return 결과 요약 문자열
     */
    public String getSummary() {
        if (!success) {
            return String.format("동기화 실패 - 콘서트: %d, 오류: %s", concertId, errorMessage);
        }

        return String.format(
                "동기화 완료 - 콘서트: %d, DB좌석: %d, Redis좌석: %d, " +
                        "불일치: %d, 누락: %d, 불필요: %d, 수정: %d, 소요시간: %dms",
                concertId, totalDbSeats, totalRedisSeats,
                inconsistentSeats, missingInRedis, extraInRedis, fixedSeats,
                getDuration().toMillis()
        );
    }

    /**
     * 빈 결과 생성 (좌석 데이터가 없는 경우)
     *
     * @param concertId 콘서트 ID
     * @param syncStartTime 동기화 시작 시간
     * @return 빈 동기화 결과
     */
    public static SeatSyncResult createEmpty(Long concertId, LocalDateTime syncStartTime) {
        return SeatSyncResult.builder()
                .concertId(concertId)
                .syncStartTime(syncStartTime)
                .syncEndTime(LocalDateTime.now())
                .totalDbSeats(0)
                .totalRedisSeats(0)
                .inconsistentSeats(0)
                .missingInRedis(0)
                .extraInRedis(0)
                .fixedSeats(0)
                .success(true)
                .build();
    }

    /**
     * 동기화 필요 여부 확인
     *
     * @return 불일치 데이터가 있으면 true
     */
    public boolean hasSyncIssues() {
        return inconsistentSeats > 0 || missingInRedis > 0 || extraInRedis > 0;
    }

    /**
     * 동기화 효과성 확인
     *
     * @return 수정된 좌석이 있으면 true
     */
    public boolean hasFixedIssues() {
        return fixedSeats > 0;
    }
}