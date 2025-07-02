package com.team03.ticketmon.booking.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;

/**
 * 예매(Booking) 엔티티에 대한 데이터 접근을 처리
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {
	@Query("""
		    select distinct b from Booking b
		    join fetch b.concert
		    left join fetch b.payment
		    left join fetch b.tickets t
		    left join fetch t.concertSeat cs
		    where b.bookingNumber = :bookingNumber
		""")
	Optional<Booking> findByBookingNumber(String bookingNumber);

	@Query("""
		    select distinct b from Booking b
		    join fetch b.concert
		    left join fetch b.payment
		    left join fetch b.tickets t
		    left join fetch t.concertSeat cs
		    where b.userId = :userId
		""")
	List<Booking> findByUserId(@Param("userId") Long userId);

	List<Booking> findByStatus(BookingStatus status);

	@Query("""
		    select distinct b from Booking b
		    join fetch b.concert
		    left join fetch b.payment
		    left join fetch b.tickets t
		    left join fetch t.concertSeat cs
		    where b.status = com.team03.ticketmon.booking.domain.BookingStatus.PENDING_PAYMENT
		      and b.createdAt <= :expirationTime
		""")
	List<Booking> findExpiredPendingBookings(@Param("expirationTime") LocalDateTime expirationTime);
}
