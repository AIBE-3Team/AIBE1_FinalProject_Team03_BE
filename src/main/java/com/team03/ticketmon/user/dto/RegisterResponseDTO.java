package com.team03.ticketmon.user.dto;

public record RegisterResponseDTO(
        boolean isSuccess,
        String type,
        String message
) {
}
