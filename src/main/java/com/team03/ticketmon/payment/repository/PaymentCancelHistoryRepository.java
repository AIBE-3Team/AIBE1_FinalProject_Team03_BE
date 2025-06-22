package com.team03.ticketmon.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team03.ticketmon.payment.domain.entity.PaymentCancelHistory;

public interface PaymentCancelHistoryRepository extends JpaRepository<PaymentCancelHistory, Long> {
}
