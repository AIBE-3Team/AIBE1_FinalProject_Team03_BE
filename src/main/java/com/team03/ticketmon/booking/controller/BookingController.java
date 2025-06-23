package com.team03.ticketmon.booking.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.booking.dto.BookingDTO;
import com.team03.ticketmon.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

/**
 * 예매(Booking)와 관련된 HTTP 요청을 처리하는 API 컨트롤러
 *
 * <p>
 * 이 컨트롤러는 클라이언트의 요청을 받아 서비스 계층에 전달하고,
 * 그 결과를 표준화된 응답 형식인 {@link SuccessResponse}로 감싸 반환
 * 모든 엔드포인트는 인증된 사용자만 접근 가능하다고 가정하며,
 * {@link AuthenticationPrincipal}을 통해 인증된 사용자 정보를 획득
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * 새로운 예매를 생성 (결제 대기 상태)
     *
     * <p>
     * 클라이언트로부터 콘서트 ID와 선택한 좌석 ID 목록을 받아,
     * Redis를 통해 좌석 선점이 유효한지 검증한 후, '결제 대기' 상태의 예매를 생성
     * 생성된 예매 정보는 다음 결제 단계로 넘어가기 위해 클라이언트에게 반환
     * </p>
     *
     * @param createRequest 예매 생성을 위한 요청 DTO (콘서트 ID, 좌석 ID 목록 포함)
     * @param user   현재 인증된 사용자의 정보
     * @return 생성된 예매의 상세 정보가 담긴 응답
     */
    @PostMapping
    public ResponseEntity<SuccessResponse<BookingDTO.PaymentReadyResponse>> createBooking(
            @Valid @RequestBody BookingDTO.CreateRequest createRequest,
            @AuthenticationPrincipal User user
    ) {

        log.info("보류중인 예약 생성 시도. for user: {}", user.getUsername());

        BookingDTO.PaymentReadyResponse responseDto = bookingService.createPendingBooking(createRequest, Long.valueOf(user.getUsername()));

        return new ResponseEntity<>(SuccessResponse.of(responseDto), HttpStatus.CREATED);
    }

    /**
     * 특정 예매를 취소합니다.
     *
     * <p>
     * 이 엔드포인트는 예매 취소 프로세스를 시작하는 역할
     * 내부적으로 결제 서비스와의 연동을 통해 결제 취소를 먼저 시도하고,
     * 성공 시 예매 상태를 'CANCELED'로 변경하고 좌석을 반환 처리
     * </p>
     *
     * @param bookingId 취소할 예매의 ID
     * @param user 현재 인증된 사용자의 정보 (취소 권한 확인용)
     * @return 처리 성공 여부만 담긴 응답 (데이터 없음)
     */
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<SuccessResponse<Void>> cancelBooking(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal User user) {

        log.info("예매 취소 시도 booking ID: {} for user: {}", bookingId, user.getUsername());
        bookingService.cancelBooking(bookingId, Long.valueOf(user.getUsername()));
        return ResponseEntity.ok(SuccessResponse.of(null));
    }
}