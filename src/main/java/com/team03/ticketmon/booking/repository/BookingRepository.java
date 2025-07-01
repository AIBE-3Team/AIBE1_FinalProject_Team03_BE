package com.team03.ticketmon.booking.repository;

import java.util.List;
import java.util.Optional;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 예매(Booking) 엔티티에 대한 데이터 접근을 처리
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Query("""
                select b from Booking b
                join fetch b.concert
                left join fetch b.payment
                left join fetch b.tickets t
                left join fetch t.concertSeat cs
                left join fetch cs.seat
                where b.bookingNumber = :bookingNumber
            """)
    Optional<Booking> findByBookingNumber(String bookingNumber);

    @Query("""
                select distinct b from Booking b
                join fetch b.concert
                left join fetch b.payment
                left join fetch b.tickets t
                left join fetch t.concertSeat cs
                left join fetch cs.seat
                where b.userId = :userId
            """)
    List<Booking> findByUserId(@Param("userId") Long userId);

    List<Booking> findByStatus(BookingStatus status); // 💡 이 줄을 추가합니다.
}
