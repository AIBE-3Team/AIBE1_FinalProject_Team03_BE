package com.team03.ticketmon.queue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 대기열 진입 요청에 대한 통합 응답 DTO.
 * null인 필드는 JSON으로 변환 시 제외하여 응답 메시지를 간결하게 유지.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnterResponse(
        String status,     // "WAITING", "IMMEDIATE_ENTRY", "ERROR"
        Long rank,         // status가 "WAITING"일 때만 값을 가짐
        String accessKey,  // status가 "IMMEDIATE_ENTRY"일 때만 값을 가짐
        String message     // 사용자에게 보여줄 메시지 (에러 또는 안내)
) {
    // 대기열 진입 시 사용할 정적 팩토리 메서드
    public static EnterResponse waiting(Long rank) {
        return new EnterResponse("WAITING", rank, null, "대기열에 정상적으로 등록되었습니다.");
    }

    // 즉시 입장 시 사용할 정적 팩토리 메서드
    public static EnterResponse immediateEntry(String accessKey) {
        return new EnterResponse("IMMEDIATE_ENTRY", null, accessKey, "즉시 입장이 가능합니다.");
    }

    // 에러 발생 시 사용할 정적 팩토리 메서드
    public static EnterResponse error(String message) {
        return new EnterResponse("ERROR", null, null, message);
    }
}