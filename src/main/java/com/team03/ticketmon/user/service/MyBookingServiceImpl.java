package com.team03.ticketmon.user.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.service.BookingService;
import com.team03.ticketmon.user.dto.UserBookingDetailDto;
import com.team03.ticketmon.user.dto.UserBookingSummaryDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MyBookingServiceImpl implements MyBookingService {

    private final UserEntityService userEntityService;
    private final BookingService bookingService;

    @Override
    public List<UserBookingSummaryDTO> findBookingList(Long userId) {
        if (!userEntityService.existsById(userId)) {
            throw new EntityNotFoundException("회원 정보가 없습니다.");
        }

        return bookingService.findBookingList(userId).stream()
                .map(booking -> new UserBookingSummaryDTO(
                        booking.getBookingId(),
                        booking.getBookingNumber(),
                        booking.getConcert().getTitle(),
                        booking.getConcert().getConcertDate(),
                        booking.getConcert().getVenueName(),
                        booking.getConcert().getVenueAddress(),
                        booking.getStatus().name(),
                        booking.getTotalAmount(),
                        booking.getConcert().getPosterImageUrl(),
                        getSeatList(booking)
                ))
                .toList();
    }

    @Override
    public UserBookingDetailDto findBookingDetail(Long userId, String bookingNumber) {
        Booking booking = bookingService.findBookingDetail(userId, bookingNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        return new UserBookingDetailDto(
                booking.getBookingId(),
                booking.getBookingNumber(),
                booking.getConcert().getTitle(),
                booking.getConcert().getArtist(),
                booking.getConcert().getConcertDate(),
                booking.getConcert().getStartTime(),
                booking.getConcert().getEndTime(),
                booking.getConcert().getVenueName(),
                booking.getConcert().getVenueAddress(),
                booking.getTotalAmount(),
                booking.getStatus().name(),
                booking.getPayment().getStatus().name(),
                booking.getPayment().getPaymentMethod(),
                getSeatList(booking),
                booking.getConcert().getPosterImageUrl(),
                booking.getCreatedAt()
        );
    }

    // 좌석 정보 가져오기
    private List<String> getSeatList(Booking booking) {
        return booking.getTickets().stream()
                .map(ticket -> {
                    var concertSeat = ticket.getConcertSeat();
                    var seat = concertSeat.getSeat();
                    return String.format("%s석 %s열 %d번",
                            concertSeat.getGrade().name(),
                            seat.getSeatRow(),
                            seat.getSeatNumber()
                    );
                }).toList();
    }
}
