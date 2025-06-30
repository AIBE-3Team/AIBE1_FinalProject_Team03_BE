package com.team03.ticketmon.queue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ✅ EnterResponse: 대기열 진입 통합 응답 DTO<br>
 * -----------------------------------------------------<br>
 * 대기열 진입 요청에 대한 상태, 순위, 접근 키, 메시지를 포함한 응답을 제공합니다.<br><br>
 *
 * 📌 상태(status):
 * <ul>
 *     <li>WAITING         : 대기열에 등록된 경우</li>
 *     <li>IMMEDIATE_ENTRY : 즉시 입장이 가능한 경우</li>
 *     <li>ERROR           : 에러 발생 시</li>
 * </ul>
 *
 * 📌 필드 설명:
 * <ul>
 *     <li>status    : 응답 상태</li>
 *     <li>rank      : 대기열 순위 (status가 WAITING일 때만 유효)</li>
 *     <li>accessKey : 즉시 입장 키 (status가 IMMEDIATE_ENTRY일 때만 유효)</li>
 *     <li>message   : 사용자 안내 메시지</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnterResponse(
        String status,     // "WAITING", "IMMEDIATE_ENTRY", "ERROR"
        Long rank,         // status가 "WAITING"일 때만 값을 가짐
        String accessKey,  // status가 "IMMEDIATE_ENTRY"일 때만 값을 가짐
        String message     // 사용자에게 보여줄 메시지 (에러 또는 안내)
) {
    /**
     * ✅ 정적 팩토리 메서드: 대기열 등록 응답 생성 (WAITING)
     *
     * @param rank 대기열 내 순위
     * @return EnterResponse 객체
     */
    public static EnterResponse waiting(Long rank) {
        return new EnterResponse("WAITING", rank, null, "대기열에 정상적으로 등록되었습니다.");
    }

    /**
     * ✅ 정적 팩토리 메서드: 즉시 입장 응답 생성 (IMMEDIATE_ENTRY)
     *
     * @param accessKey 사용자에게 부여된 접근 키
     * @return EnterResponse 객체
     */
    public static EnterResponse immediateEntry(String accessKey) {
        return new EnterResponse("IMMEDIATE_ENTRY", null, accessKey, "즉시 입장이 가능합니다.");
    }

    /**
     * ✅ 정적 팩토리 메서드: 에러 응답 생성 (ERROR)
     *
     * @param message 사용자에게 보여줄 에러 메시지
     * @return EnterResponse 객체
     */
    public static EnterResponse error(String message) {
        return new EnterResponse("ERROR", null, null, message);
    }
}