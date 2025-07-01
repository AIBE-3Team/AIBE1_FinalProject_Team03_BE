package com.team03.ticketmon.user.service;

import com.team03.ticketmon.user.dto.UserBookingDetailDto;
import com.team03.ticketmon.user.dto.UserBookingSummaryDTO;

import java.util.List;

public interface MyBookingService {
    List<UserBookingSummaryDTO> findBookingList(Long userId);
    UserBookingDetailDto findBookingDetail(Long userId, String bookingNumber);
    void cancelBooking(Long userId, Long bookingId);
}
