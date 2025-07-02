package com.team03.ticketmon.seat.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * 좌석 동기화 분석 결과 DTO
 *
 * DB와 Redis 간 좌석 상태 차이점을 분석한 결과를 담는 클래스
 *
 * 기능:
 * - 누락/불필요/불일치 좌석 ID 집합 관리
 * - 동기화 필요 여부 판단
 * - 분석 결과 요약 제공
 */
@Getter
@Builder
public class SeatSyncAnalysis {

    /** Redis에 누락된 좌석 ID 집합 (DB에만 존재) */
    private final Set<Long> missingInRedis;

    /** Redis에만 존재하는 불필요한 좌석 ID 집합 (DB에는 없음) */
    private final Set<Long> extraInRedis;

    /** 상태가 불일치한 좌석 ID 집합 (DB와 Redis 상태 다름) */
    private final Set<Long> inconsistentSeats;

    /**
     * 동기화 필요 여부 확인
     *
     * @return 불일치 데이터가 하나라도 있으면 true
     */
    public boolean needsSync() {
        return !missingInRedis.isEmpty() ||
                !extraInRedis.isEmpty() ||
                !inconsistentSeats.isEmpty();
    }

    /**
     * 총 문제 좌석 수 계산
     *
     * @return 전체 불일치 좌석 수
     */
    public int getTotalIssues() {
        return missingInRedis.size() + extraInRedis.size() + inconsistentSeats.size();
    }

    /**
     * 분석 결과 요약 메시지 생성
     *
     * @return 분석 결과 요약
     */
    public String getSummary() {
        if (!needsSync()) {
            return "동기화 불필요 - 모든 좌석 상태가 일치함";
        }

        return String.format(
                "동기화 필요 - 누락: %d개, 불필요: %d개, 불일치: %d개 (총 %d개 문제)",
                missingInRedis.size(), extraInRedis.size(),
                inconsistentSeats.size(), getTotalIssues()
        );
    }

    /**
     * 빈 분석 결과 생성 (모든 상태가 일치하는 경우)
     *
     * @return 문제가 없는 분석 결과
     */
    public static SeatSyncAnalysis createEmpty() {
        return SeatSyncAnalysis.builder()
                .missingInRedis(Set.of())
                .extraInRedis(Set.of())
                .inconsistentSeats(Set.of())
                .build();
    }
}