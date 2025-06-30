package com.team03.ticketmon.booking.repository;

import java.util.List;
import java.util.Optional;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 예매(Booking) 엔티티에 대한 데이터 접근을 처리
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {
	Optional<Booking> findByBookingNumber(String bookingNumber);
	List<Booking> findByUserId(Long userId);
	List<Booking> findByStatus(BookingStatus status); // 💡 이 줄을 추가합니다.

}
