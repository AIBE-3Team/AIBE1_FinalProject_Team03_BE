package com.team03.ticketmon.payment.domain.entity;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // DB 컬럼 이름에 맞춤
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    // 💡 [수정] user_id 컬럼에 직접 매핑될 필드 추가
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(unique = true, length = 200)
    private String paymentKey;

    @Column(length = 50)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private LocalDateTime approvedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 취소 이력 (1:1)
     */
    @OneToOne(
            mappedBy = "payment",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private PaymentCancelHistory cancelHistory;

    
    // 취소 시 호출될 수 있는 헬퍼
    public void setCancelHistory(PaymentCancelHistory history) {
        this.cancelHistory = history;
        history.setPayment(this);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Builder
    public Payment(Booking booking, Long userId, String orderId, BigDecimal amount) {
        this.booking = booking;
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public void complete(String paymentKey, LocalDateTime approvedAt) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.DONE;
        this.approvedAt = approvedAt;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELED;
    }
}
