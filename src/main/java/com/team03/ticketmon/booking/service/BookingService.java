package com.team03.ticketmon.booking.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import com.team03.ticketmon.booking.dto.BookingCreateRequest;
import com.team03.ticketmon.booking.repository.BookingRepository;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.service.SeatStatusService;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 예매(Booking)와 관련된 핵심 비즈니스 로직을 처리하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ConcertRepository concertRepository;
    private final ConcertSeatRepository concertSeatRepository;
    private final SeatStatusService seatStatusService;

    /**
     * '결제 대기' 상태의 새로운 예매를 생성
     * 이 메서드는 Redis를 통해 좌석 선점이 완료된 후에 호출되어야 한다
     *
     * @param createDto 예매 생성에 필요한 데이터(콘서트 ID, 좌석 ID 목록)
     * @param userId    예매를 요청한 사용자의 ID
     * @return 생성된 Booking 엔티티
     * @throws BusinessException     콘서트 또는 좌석 정보를 찾을 수 없을 때 (ErrorCode.CONCERT_NOT_FOUND)
     * @throws IllegalStateException       선점된 좌석의 상태가 유효하지 않을 때 (다른 사용자가 선점했거나, 이미 예매된 경우)
     */
    @Transactional
    public Booking createPendingBooking(BookingCreateRequest createDto, Long userId) {

        // 0. 유저 정보 조회
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 1. 콘서트 정보 조회
        Concert concert = concertRepository.findById(createDto.getConcertId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        // 2. 선택된 좌석 목록 및 총액 계산
        List<ConcertSeat> selectedSeats = concertSeatRepository.findAllById(createDto.getConcertSeatIds());
        if (selectedSeats.size() != createDto.getConcertSeatIds().size()) {
            throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
        }

        // 2-1. 모든 좌석의 선점 상태를 한번에 검증 (로직은 동일)
        selectedSeats.forEach(seat ->
                validateSeatReservation(seat.getConcert().getConcertId(), seat.getConcertSeatId(), userId)
        );

        // 3. Ticket & Booking 생성
        Booking booking = Booking.createBooking(userId, concert, selectedSeats);

        // 4. Booking 저장
        Booking savedBooking = bookingRepository.save(booking);
        log.info("결제 대기 상태의 예매 생성 완료. Booking ID: {}", savedBooking.getBookingId());

        return savedBooking;
    }

    @Transactional
    public List<Booking> findBookingList(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return bookingRepository.findByUserId(userId);
    }

    @Transactional
    public Optional<Booking> findBookingDetail(Long userId, String bookingNumber) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return bookingRepository.findByBookingNumber(bookingNumber);
    }

    /**
     * 예매와 관련된 내부 상태를 '취소'로 최종 처리
     * 이 메서드는 외부 시스템(결제)과의 연동이 성공한 후 호출되어야 한다.
     *
     * @param booking 취소할 Booking 엔티티
     */
    @Transactional
    public void finalizeCancellation(Booking booking) {
        // 1. 예매 상태를 CANCELED로 변경
        booking.cancel();

        // [좌석 반환] 예매된 좌석들을 다시 'AVAILABLE' 상태로 변경하는 로직 추가,
         booking.getTickets().forEach(ticket ->
             seatStatusService.releaseSeat(
                 booking.getConcert().getConcertId(),
                 ticket.getConcertSeat().getSeat().getSeatId(),
                 booking.getUserId()
             )
         );

        // 히스토리 테이블로 이관하는 로직 호출
        archiveBookingAndTickets(booking);

        bookingRepository.delete(booking);
        log.info("예매가 성공적으로 취소(삭제)되었습니다. Booking ID: {}", booking.getBookingId());
    }

    /**
     * 예매 취소 요청의 유효성을 검사
     * 취소할 예매 엔티티를 반환
     *
     * @param bookingId 검사할 예매 ID
     * @param userId 요청한 사용자 ID
     * @return 검증이 완료된 Booking 엔티티
     * @throws BusinessException 유효성 검사 실패 시 (소유권, 상태, 취소 기간 등)
     */
    @Transactional(readOnly = true)
    public Booking validateCancellableBooking(Long bookingId, Long userId) {
        // 1. 예매 정보 조회
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        // 2. 예매 취소 권한 확인
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 3. 이미 취소되었거나 완료된 예매인지 확인
        if (booking.getStatus() == BookingStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED_BOOKING);
        }

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_COMPLETED_BOOKING);
        }

        // TODO: [취소 정책] 상세한 비즈니스 규칙을 추가 (예: 공연 1일 전까지 가능)(현재 하드코딩)
        // 4. 취소 정책 검증 (예: 공연 시작일 하루 전까지만 가능)
        if (booking.getConcert().getConcertDate().isBefore(LocalDate.now().plusDays(1))) {
            throw new BusinessException(ErrorCode.CANCELLATION_PERIOD_EXPIRED);
        }

        return booking;
    }

    /**
     * Redis의 좌석 상태를 확인하여, 해당 좌석이 주어진 사용자에 의해 유효하게 선점되었는지 검증합니다.
     */
    private void validateSeatReservation(Long concertId, Long seatId, Long userId) {
        seatStatusService.getSeatStatus(concertId, seatId)
                .filter(status -> status.isReserved() && userId.equals(status.getUserId()) && !status.isExpired())
                .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN, "좌석 선점 정보가 유효하지 않습니다. Seat ID: " + seatId));
    }

    /**
     * [가짜 메서드] 예매와 티켓 정보를 히스토리 테이블로 이관
     * 실제 서비스에서 이 부분에 데이터 이관을 고려함
     *
     * @param booking 이관할 예매 정보
     */
    private void archiveBookingAndTickets(Booking booking) {

        // [미래 구현] 아래 로직을 실제 히스토리 DB에 맞게 구현

        // 1. 변경된 상태가 반영된 객체를 기반으로 History 객체를 생성합니다.
        //    BookingHistory bookingHistory = BookingHistory.from(booking);

        // 2. History DB에 저장합니다.
        //    bookingHistoryRepository.save(bookingHistory);
        //    ... (Ticket 히스토리 저장 로직) ...

        log.info("[시뮬레이션] Booking ID {} 및 관련 Ticket 정보를 히스토리 테이블로 이관 완료.", booking.getBookingId());
    }
}


