package com.team03.ticketmon.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.payment.domain.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByOrderId(String orderId);

	List<Payment> findByUserId(Long userId); // Booking의 UserId로 Payment 찾기

	Optional<Payment> findByBooking(Booking booking);

	// PaymentRepository.java
	@Query("SELECT p FROM Payment p JOIN FETCH p.booking WHERE p.orderId = :orderId")
	Optional<Payment> findWithBookingByOrderId(@Param("orderId") String orderId);

}
