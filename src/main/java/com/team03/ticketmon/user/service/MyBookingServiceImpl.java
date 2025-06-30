package com.team03.ticketmon.user.service;

import com.team03.ticketmon.booking.service.BookingService;
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
        userEntityService.findUserEntityByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("회원 정보가 없습니다."));

        return bookingService.findBookingList(userId).stream()
                .map(booking -> new UserBookingSummaryDTO(
                        booking.getBookingNumber(),
                        booking.getConcert().getTitle(),
                        booking.getConcert().getConcertDate(),
                        booking.getStatus().toString(),
                        booking.getTotalAmount(),
                        booking.getConcert().getPosterImageUrl()
                ))
                .toList();
    }

    @Override
    public void cancelBooking(Long userId, Long bookingId) {

    }
}
