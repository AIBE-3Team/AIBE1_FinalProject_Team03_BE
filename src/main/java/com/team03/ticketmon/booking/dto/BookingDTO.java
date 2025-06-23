package com.team03.ticketmon.booking.dto;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 예매(Booking) 도메인과 관련된 모든 DTO를 관리하는 클래스
 * Controller와 Service 간의 데이터 전송에 사용
 */
public class BookingDTO {

    /**
     * 예매 생성을 위한 클라이언트의 요청 데이터를 담는 DTO
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "콘서트 ID는 필수입니다.")
        private Long concertId;
        @NotEmpty(message = "좌석을 하나 이상 선택해야 합니다.")
        private List<Long> concertSeatIds;
    }

    /**
     * 예매 생성 직후, 클라이언트가 토스페이먼츠에 결제를 요청하기 위해 필요한 정보를 담는 응답 DTO
     */
    @Getter
    @Builder
    public static class PaymentReadyResponse {
        // for Toss Payments SDK
        private String orderId;       // 주문 ID (우리 시스템의 bookingNumber)
        private String orderName;     // 주문명 (예: "아이유 콘서트 'Love, Poem' 외 1매")
        private BigDecimal amount;    // 총 결제 금액

        // for Client
        private String customerName;  // 구매자 이름
        private String customerEmail; // 구매자 이메일
        private Long bookingId;       // 우리 시스템의 예매 ID (결제 후 상태 검증용)
    }

    /**
     * 예매 상세 정보 조회를 위한 응답 DTO
     */
    @Getter
    @Builder
    public static class BookingDetailResponse {
        private Long bookingId;
        private String bookingNumber;
        private BookingStatus status;
        private BigDecimal totalAmount;
        private LocalDateTime bookingDate;

        private ConcertInfo concertInfo;
        private List<TicketInfo> tickets;

        /**
         * Booking 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드
         */
        public static BookingDetailResponse from(Booking booking) {
            return BookingDetailResponse.builder()
                    .bookingId(booking.getBookingId())
                    .bookingNumber(booking.getBookingNumber())
                    .status(booking.getStatus())
                    .totalAmount(booking.getTotalAmount())
                    // .bookingDate(booking.getCreatedAt()) // BaseEntity 등 생성일 필드 사용
                    .concertInfo(ConcertInfo.from(booking.getConcert()))
                    .tickets(booking.getTickets().stream()
                            .map(TicketInfo::from)
                            .collect(Collectors.toList()))
                    .build();
        }
    }

    /**
     * 예매 목록의 각 항목을 위한 간단한 응답 DTO
     */
    @Getter
    @Builder
    public static class BookingSimpleResponse {
        private Long bookingId;
        private String bookingNumber;
        private String concertTitle;
        private LocalDateTime bookingDate;
        private BookingStatus status;
        private BigDecimal totalAmount;

        public static BookingSimpleResponse from(Booking booking) {
            return BookingSimpleResponse.builder()
                    .bookingId(booking.getBookingId())
                    .bookingNumber(booking.getBookingNumber())
                    .concertTitle(booking.getConcert().getTitle())
                    // .bookingDate(booking.getCreatedAt())
                    .status(booking.getStatus())
                    .totalAmount(booking.getTotalAmount())
                    .build();
        }
    }

    // --- 중첩된 정보 DTO 들 ---

    @Getter
    @Builder
    private static class ConcertInfo {
        private String concertTitle;
        private LocalDateTime concertDate;
        private String venueName;

        public static ConcertInfo from(com.team03.ticketmon.concert.domain.Concert concert) {
            return ConcertInfo.builder()
                    .concertTitle(concert.getTitle())
                    // .concertDate(concert.getConcertDate())
                    // .venueName(concert.getVenue().getName())
                    .build();
        }
    }

    @Getter
    @Builder
    private static class TicketInfo {
        private String ticketNumber;
        private String seatGrade;
        private String seatNumber;
        private BigDecimal price;

        public static TicketInfo from(com.team03.ticketmon.booking.domain.Ticket ticket) {
            return TicketInfo.builder()
                    .ticketNumber(ticket.getTicketNumber())
                    // .seatGrade(ticket.getConcertSeat().getSeat().getGrade())
                    // .seatNumber(ticket.getConcertSeat().getSeat().getSeatNumber())
                    .price(ticket.getPrice())
                    .build();
        }
    }
}