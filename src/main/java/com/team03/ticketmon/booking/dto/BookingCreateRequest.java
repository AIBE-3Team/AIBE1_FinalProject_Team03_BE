package com.team03.ticketmon.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

/**
 * 예매 생성을 위한 클라이언트의 요청 데이터를 담는 DTO
 */
@Getter
public class BookingCreateRequest {
    @NotNull(message = "콘서트 ID는 필수입니다.")
    private Long concertId;
    @NotEmpty(message = "좌석을 하나 이상 선택해야 합니다.")
    private List<Long> concertSeatIds;
}
