package com.team03.ticketmon.seat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 전체 콘서트 동기화 결과 DTO
 *
 * 모든 활성 콘서트의 좌석 상태 동기화 작업 결과를 담는 record 클래스
 *
 * 기능:
 * - 전체 동기화 작업 통계
 * - 성공/실패 콘서트 수
 * - 전체 작업 소요 시간
 */
@Getter
@Builder
public class AllConcertSyncResult {

    /** 동기화 시작 시간 */
    private final LocalDateTime syncStartTime;

    /** 동기화 종료 시간 */
    private final LocalDateTime syncEndTime;

    /** 총 콘서트 수 */
    private final int totalConcerts;

    /** 성공한 콘서트 수 */
    private final int successCount;

    /** 실패한 콘서트 수 */
    private final int failCount;

    /** 전체 작업 성공 여부 */
    private final boolean success;

    /** 추가 메시지 */
    private final String message;

    /**
     * 전체 동기화 소요 시간 계산
     *
     * @return 소요 시간 (Duration)
     */
    public Duration getTotalDuration() {
        if (syncStartTime == null || syncEndTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(syncStartTime, syncEndTime);
    }

    /**
     * 성공률 계산
     *
     * @return 성공률 (0.0 ~ 1.0)
     */
    public double getSuccessRate() {
        if (totalConcerts == 0) {
            return 0.0;
        }
        return (double) successCount / totalConcerts;
    }

    /**
     * 전체 동기화 결과 요약 메시지 생성
     *
     * @return 결과 요약 문자열
     */
    public String getSummary() {
        if (!success) {
            return String.format("전체 동기화 실패 - %s", message);
        }

        return String.format(
                "전체 동기화 완료 - 총 콘서트: %d, 성공: %d, 실패: %d, " +
                        "성공률: %.1f%%, 소요시간: %dms",
                totalConcerts, successCount, failCount,
                getSuccessRate() * 100, getTotalDuration().toMillis()
        );
    }
}