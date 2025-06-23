package com.team03.ticketmon.payment.repository;

import java.util.List;
import java.util.Optional;

import com.team03.ticketmon.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import com.team03.ticketmon.payment.domain.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findByOrderId(String orderId);

	List<Payment> findByBooking_UserId(Long userId); // Booking의 UserId로 Payment 찾기

	Optional<Payment> findByBooking(Booking booking);

}
