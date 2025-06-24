package com.team03.ticketmon.booking.facade;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.dto.BookingCreateRequest;
import com.team03.ticketmon.booking.service.BookingService;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.repository.PaymentRepository;
import com.team03.ticketmon.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예매‑결제 유즈케이스를 하나의 트랜잭션으로 오케스트레이션
 * 하나의 유스케이스를 위한 트랜잭션 단위를 정의
 */
@Service
@RequiredArgsConstructor
public class BookingFacadeService {

    private final BookingService bookingService;
    private final PaymentService paymentService;

    /**
     *  좌석 선점 검증 →  PENDING 예매 생성 →  결제 정보(Payment) 생성·반환
     * @param createRequest 콘서트 ID, 콘서트좌석 IDs
     * @param userId 예매를 요청한 사용자 ID
     */
    @Transactional
    public PaymentExecutionResponse createBookingAndInitiatePayment(BookingCreateRequest createRequest, Long userId) {

        // 1. 예매 생성(PENDING_PAYMENT 상태)
        Booking pendingBooking = bookingService.createPendingBooking(createRequest, userId);

        // 2. PaymentService에 위임해서 “(PENDING)” 객체를 찾거나 생성
        return paymentService.initiatePayment(pendingBooking);
    }

    /**
     * 예매와 연결된 결제를 취소하고, 성공 시 예매 상태를 업데이트하는 전체 과정을 처리
     * 외부 시스템(결제 PG) 연동을 먼저 수행하여 데이터 정합성을 보장
     *
     * @param bookingId 취소할 예매 ID
     * @param userId 취소를 요청한 사용자 ID
     */
    @Transactional
    public void cancelBookingAndPayment(Long bookingId, Long userId) {

        // 1. 예매가 취소 가능한 상태인지, 요청자가 소유자인지 검증
        Booking booking = bookingService.validateCancellableBooking(bookingId, userId);

        // 2. PaymentService가 booking 내부에서 결제 취소 및 상태 갱신 처리
        paymentService.cancelPayment(booking, "사용자 예매 취소");


        // 5. 외부 시스템 연동 성공 후, 내부 DB의 예매 상태 및 관련 데이터를 최종적으로 변경
        bookingService.finalizeCancellation(booking);


        // TODO: [알림] 사용자에게 취소 완료 알림 전송 (SMS, 이메일 등)
        // notificationService.notifyCancellationSuccess(userId, bookingId);
    }


}
