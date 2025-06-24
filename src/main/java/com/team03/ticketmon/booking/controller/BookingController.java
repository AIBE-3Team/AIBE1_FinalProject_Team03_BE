package com.team03.ticketmon.booking.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.booking.dto.BookingCreateRequest;
import com.team03.ticketmon.booking.facade.BookingFacadeService;
import com.team03.ticketmon.booking.service.BookingService;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@Tag(name = "Booking API", description = "예매 생성, 취소 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingFacadeService bookingFacadeService; // Facade 주입


    @Operation(summary = "예매 생성 및 결제 준비", description = "좌석 선점 후 예매를 생성하고, 즉시 결제에 필요한 정보를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "예매 정보 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 입력 (좌석 ID 누락 등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 콘서트 또는 좌석"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 선택되었거나 선점 정보가 유효하지 않은 좌석")
    })
    @PostMapping
    public ResponseEntity<SuccessResponse<PaymentExecutionResponse>> createBookingAndPreparePayment(
            @Valid @RequestBody BookingCreateRequest createRequest,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        log.info("예매 생성 및 결제 준비 시도. for user: {}", user.getUsername());

        PaymentExecutionResponse responseDto = bookingFacadeService.createBookingAndInitiatePayment(createRequest, user.getUserId());

        return new ResponseEntity<>(SuccessResponse.of("예매 생성 및 결제 정보 조회가 완료되었습니다.", responseDto), HttpStatus.CREATED);
    }

    /**
     * 예매를 취소. 이 요청은 결제 취소를 포함한 모든 과정을 동기적으로 처리
     * @param bookingId 취소할 예매의 ID
     * @param user 현재 인증된 사용자 정보
     * @return 취소 성공 메시지
     */
    @Operation(summary = "예매 취소 (동기)", description = "특정 예매를 취소 처리합니다. 성공 시 연동된 결제도 함께 취소됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자 (토큰 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "취소 권한 없음 (타인의 예매)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 예매"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 충돌 (이미 취소 또는 완료된 예매)")
    })
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<SuccessResponse<Void>> cancelBooking(
            @Parameter(description = "취소할 예매의 ID", required = true) @PathVariable Long bookingId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {

        log.info("예매 취소 시도 booking ID: {} for user: {}", bookingId, user);

        bookingFacadeService.cancelBookingAndPayment(bookingId, user.getUserId());

        // TODO: 향후 비동기 처리 방식으로 전환 고려.
        // 현재는 동기 처리 후 즉시 성공 응답을 반환하지만,
        // 미래에는 202 Accepted를 반환하고 백그라운드에서 처리 후 알림을 주는 방식으로 개선할 수 있음.

        return ResponseEntity.ok(SuccessResponse.of("예매가 성공적으로 취소되었습니다.", null));
    }
}