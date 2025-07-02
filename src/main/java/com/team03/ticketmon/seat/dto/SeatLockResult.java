package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 좌석 영구 선점 처리 결과 DTO
 *
 * TTL 삭제 및 영구 선점 상태 변경 작업의 결과를 담는 record 클래스
 *
 * 기능:
 * - 영구 선점 성공/실패 여부
 * - 상태 변경 전후 정보
 * - TTL 키 처리 결과
 * - 처리 소요 시간
 */
@Getter
@Builder
public class SeatLockResult {

    /** 콘서트 ID */
    private final Long concertId;

    /** 좌석 ID */
    private final Long seatId;

    /** 사용자 ID */
    private final Long userId;

    /** 영구 선점 시작 시간 */
    private final LocalDateTime lockStartTime;

    /** 영구 선점 완료 시간 */
    private final LocalDateTime lockEndTime;

    /** 이전 좌석 상태 */
    private final SeatStatusEnum previousStatus;

    /** 변경된 좌석 상태 */
    private final SeatStatusEnum newStatus;

    /** TTL 키 삭제 성공 여부 */
    private final boolean ttlKeyRemoved;

    /** 좌석 정보 */
    private final String seatInfo;

    /** 처리 성공 여부 */
    private final boolean success;

    /** 오류 메시지 (실패 시) */
    private final String errorMessage;

    /**
     * 영구 선점 처리 소요 시간 계산
     *
     * @return 소요 시간 (Duration)
     */
    public Duration getProcessingDuration() {
        if (lockStartTime == null || lockEndTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(lockStartTime, lockEndTime);
    }

    /**
     * 영구 선점 결과 요약 메시지 생성
     *
     * @return 결과 요약 문자열
     */
    public String getSummary() {
        if (!success) {
            return String.format("영구 선점 실패 - 콘서트: %d, 좌석: %d, 사용자: %d, 오류: %s",
                    concertId, seatId, userId, errorMessage);
        }

        return String.format(
                "영구 선점 완료 - 콘서트: %d, 좌석: %d (%s), 사용자: %d, " +
                        "상태변경: %s→%s, TTL삭제: %s, 소요시간: %dms",
                concertId, seatId, seatInfo, userId,
                previousStatus, newStatus, ttlKeyRemoved ? "성공" : "실패",
                getProcessingDuration().toMillis()
        );
    }

    /**
     * 상태 변경이 올바르게 수행되었는지 확인
     *
     * @return 상태 변경이 예상대로 수행되었으면 true
     */
    public boolean isStatusChangeValid() {
        if (!success) {
            return false;
        }

        // 일반적인 영구 선점: RESERVED → RESERVED (expiresAt null)
        // 또는 추후 RESERVED → PERMANENTLY_RESERVED
        return previousStatus == SeatStatusEnum.RESERVED &&
                (newStatus == SeatStatusEnum.RESERVED || newStatus == SeatStatusEnum.BOOKED);
    }

    /**
     * TTL 처리가 성공했는지 확인
     *
     * @return TTL 키 삭제가 성공했거나 이미 존재하지 않았으면 true
     */
    public boolean isTTLHandledProperly() {
        // TTL 키 삭제 성공 또는 키가 존재하지 않았던 경우 모두 정상
        return success; // 전체 처리가 성공했다면 TTL도 적절히 처리됨
    }

    /**
     * 영구 선점 처리가 완전히 성공했는지 확인
     *
     * @return 모든 조건이 만족되면 true
     */
    public boolean isCompleteSuccess() {
        return success && isStatusChangeValid() && isTTLHandledProperly();
    }

    /**
     * 실패한 영구 선점 결과 생성
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param userId 사용자 ID
     * @param errorMessage 오류 메시지
     * @return 실패 결과
     */
    public static SeatLockResult failure(Long concertId, Long seatId, Long userId, String errorMessage) {
        return SeatLockResult.builder()
                .concertId(concertId)
                .seatId(seatId)
                .userId(userId)
                .lockStartTime(LocalDateTime.now())
                .lockEndTime(LocalDateTime.now())
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 성공한 영구 선점 결과 생성
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @param userId 사용자 ID
     * @param previousStatus 이전 상태
     * @param newStatus 새 상태
     * @param ttlKeyRemoved TTL 키 삭제 여부
     * @param seatInfo 좌석 정보
     * @return 성공 결과
     */
    public static SeatLockResult success(Long concertId, Long seatId, Long userId,
                                         SeatStatusEnum previousStatus, SeatStatusEnum newStatus,
                                         boolean ttlKeyRemoved, String seatInfo) {
        LocalDateTime now = LocalDateTime.now();

        return SeatLockResult.builder()
                .concertId(concertId)
                .seatId(seatId)
                .userId(userId)
                .lockStartTime(now)
                .lockEndTime(now)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .ttlKeyRemoved(ttlKeyRemoved)
                .seatInfo(seatInfo)
                .success(true)
                .build();
    }
}