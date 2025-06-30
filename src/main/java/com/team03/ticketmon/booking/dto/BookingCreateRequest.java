package com.team03.ticketmon.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

/**
 * ✅ BookingCreateRequest: 예매 생성 요청 DTO<br>
 * -----------------------------------------------------<br>
 * 클라이언트로부터 예매 생성 요청 시 전달되는 데이터를 캡슐화합니다.<br><br>
 *
 * 📌 필드 설명:
 * <ul>
 *     <li>concertId      : 예매할 콘서트의 고유 ID (필수, NotNull)</li>
 *     <li>concertSeatIds : 선택한 좌석 ID 목록 (필수, 최소 1개 이상 NotEmpty)</li>
 * </ul>
 */
@Getter
public class BookingCreateRequest {

    /**
     * 🎫 예매할 콘서트의 ID<br>
     * • null일 수 없습니다. (NotNull)
     */
    @NotNull(message = "콘서트 ID는 필수입니다.")
    private Long concertId;

    /**
     * 🎟️ 선택한 좌석들의 ID 목록<br>
     * • 하나 이상 반드시 선택해야 합니다. (NotEmpty)
     */
    @NotEmpty(message = "좌석을 하나 이상 선택해야 합니다.")
    private List<Long> concertSeatIds;
}
