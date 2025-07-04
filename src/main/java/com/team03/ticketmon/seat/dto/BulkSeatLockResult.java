package com.team03.ticketmon.seat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 다중 좌석 영구 선점 처리 결과 DTO
 *
 * 사용자가 선점한 모든 좌석에 대한 일괄 영구 선점/복원 작업의 결과를 담는 record 클래스
 *
 * 기능:
 * - 전체 처리 성공/실패 여부
 * - 개별 좌석별 상세 결과
 * - 성공/실패 통계
 * - 처리 소요 시간
 */
@Getter
@Builder
public class BulkSeatLockResult {

    /** 콘서트 ID */
    private final Long concertId;

    /** 사용자 ID */
    private final Long userId;

    /** 전체 처리 시작 시간 */
    private final LocalDateTime bulkStartTime;

    /** 전체 처리 완료 시간 */
    private final LocalDateTime bulkEndTime;

    /** 개별 좌석 처리 결과 목록 */
    private final List<SeatLockResult> seatResults;

    /** 전체 좌석 수 */
    private final int totalSeats;

    /** 성공한 좌석 수 */
    private final int successCount;

    /** 실패한 좌석 수 */
    private final int failureCount;

    /** 전체 처리 성공 여부 (모든 좌석이 성공해야 true) */
    private final boolean allSuccess;

    /** 부분 성공 여부 (일부 좌석이라도 성공하면 true) */
    private final boolean partialSuccess;

    /** 처리 유형 (LOCK 또는 RESTORE) */
    private final BulkOperationType operationType;

    /** 전체 오류 메시지 (전체 실패 시) */
    private final String errorMessage;

    /**
     * 일괄 처리 소요 시간 계산
     *
     * @return 소요 시간 (Duration)
     */
    public Duration getTotalProcessingDuration() {
        if (bulkStartTime == null || bulkEndTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(bulkStartTime, bulkEndTime);
    }

    /**
     * 성공한 좌석 목록 조회
     *
     * @return 성공한 좌석의 SeatLockResult 목록
     */
    public List<SeatLockResult> getSuccessfulSeats() {
        return seatResults.stream()
                .filter(SeatLockResult::isSuccess)
                .collect(Collectors.toList());
    }

    /**
     * 실패한 좌석 목록 조회
     *
     * @return 실패한 좌석의 SeatLockResult 목록
     */
    public List<SeatLockResult> getFailedSeats() {
        return seatResults.stream()
                .filter(result -> !result.isSuccess())
                .collect(Collectors.toList());
    }

    /**
     * 성공률 계산
     *
     * @return 성공률 (0.0 ~ 1.0)
     */
    public double getSuccessRate() {
        if (totalSeats == 0) {
            return 0.0;
        }
        return (double) successCount / totalSeats;
    }

    /**
     * 일괄 처리 결과 요약 메시지 생성
     *
     * @return 결과 요약 문자열
     */
    public String getSummary() {
        String operationName = (operationType == BulkOperationType.LOCK) ? "영구 선점" : "상태 복원";

        if (totalSeats == 0) {
            return String.format("일괄 %s - 콘서트: %d, 사용자: %d, 처리할 좌석 없음",
                    operationName, concertId, userId);
        }

        if (allSuccess) {
            return String.format(
                    "일괄 %s 완료 - 콘서트: %d, 사용자: %d, 전체: %d석, 성공: %d석, " +
                            "소요시간: %dms",
                    operationName, concertId, userId, totalSeats, successCount,
                    getTotalProcessingDuration().toMillis()
            );
        } else if (partialSuccess) {
            return String.format(
                    "일괄 %s 부분 성공 - 콘서트: %d, 사용자: %d, 전체: %d석, 성공: %d석, 실패: %d석, " +
                            "성공률: %.1f%%, 소요시간: %dms",
                    operationName, concertId, userId, totalSeats, successCount, failureCount,
                    getSuccessRate() * 100, getTotalProcessingDuration().toMillis()
            );
        } else {
            return String.format(
                    "일괄 %s 실패 - 콘서트: %d, 사용자: %d, 전체: %d석, 모든 좌석 실패, " +
                            "오류: %s",
                    operationName, concertId, userId, totalSeats,
                    errorMessage != null ? errorMessage : "개별 좌석 오류 확인 필요"
            );
        }
    }

    /**
     * 상세 결과 메시지 생성 (개별 좌석별 결과 포함)
     *
     * @return 상세 결과 문자열
     */
    public String getDetailedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(getSummary()).append("\n");

        if (!seatResults.isEmpty()) {
            sb.append("개별 좌석 결과:\n");
            for (SeatLockResult result : seatResults) {
                sb.append("  - 좌석 ").append(result.getSeatId())
                        .append(": ").append(result.isSuccess() ? "성공" : "실패");
                if (!result.isSuccess() && result.getErrorMessage() != null) {
                    sb.append(" (").append(result.getErrorMessage()).append(")");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 전체 성공 결과 생성
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @param seatResults 개별 좌석 결과 목록
     * @param operationType 처리 유형
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 성공 결과
     */
    public static BulkSeatLockResult allSuccess(Long concertId, Long userId,
                                                List<SeatLockResult> seatResults,
                                                BulkOperationType operationType,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        int successCount = (int) seatResults.stream().filter(SeatLockResult::isSuccess).count();

        return BulkSeatLockResult.builder()
                .concertId(concertId)
                .userId(userId)
                .bulkStartTime(startTime)
                .bulkEndTime(endTime)
                .seatResults(seatResults)
                .totalSeats(seatResults.size())
                .successCount(successCount)
                .failureCount(seatResults.size() - successCount)
                .allSuccess(successCount == seatResults.size())
                .partialSuccess(successCount > 0)
                .operationType(operationType)
                .build();
    }

    /**
     * 전체 실패 결과 생성
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @param operationType 처리 유형
     * @param errorMessage 오류 메시지
     * @return 실패 결과
     */
    public static BulkSeatLockResult failure(Long concertId, Long userId,
                                             BulkOperationType operationType, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();

        return BulkSeatLockResult.builder()
                .concertId(concertId)
                .userId(userId)
                .bulkStartTime(now)
                .bulkEndTime(now)
                .seatResults(List.of())
                .totalSeats(0)
                .successCount(0)
                .failureCount(0)
                .allSuccess(false)
                .partialSuccess(false)
                .operationType(operationType)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 처리 유형 열거형
     */
    public enum BulkOperationType {
        LOCK("영구 선점"),
        RESTORE("상태 복원");

        private final String description;

        BulkOperationType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}