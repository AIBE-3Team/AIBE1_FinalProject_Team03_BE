package com.team03.ticketmon.queue.dto;

/**
 * ✅ AdmissionEvent: 사용자 입장 허가 이벤트 DTO<br>
 * -----------------------------------------------------<br>
 * Redis Pub/Sub 채널을 통해 사용자에게 입장 허가 정보를 전달합니다.<br><br>
 *
 * 📌 필드:
 * <ul>
 *     <li>userId    : 입장 허가를 받은 사용자 ID</li>
 *     <li>accessKey : 서비스 접근 시 필요한 고유 키</li>
 * </ul>
 */
public record AdmissionEvent(Long userId, String accessKey) {}