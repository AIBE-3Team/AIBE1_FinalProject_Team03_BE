package com.team03.ticketmon.queue.dto;

/**
 * 사용자에게 입장 허가를 알리기 위해 Redis Pub/Sub 채널로 전송되는 DTO
 *
 * @param userId    입장 허가를 받은 사용자 ID
 * @param accessKey 사용자에게 부여된, 서비스 접근 시 필요한 고유 키
 */
public record AdmissionEvent(Long userId, String accessKey) {}