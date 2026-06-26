package com.fooddelivery.payment.domain.model;

import com.fooddelivery.payment.domain.model.valueobject.Money;
import com.fooddelivery.payment.domain.model.valueobject.RefundStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.payment.domain.util.UuidCreator;

@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "gateway_refund_id")
    private String gatewayRefundId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Refund(UUID id, UUID paymentId, Money amount, String reason) {
        this.id = id;
        this.paymentId = paymentId;
        this.amount = amount.amount();
        this.reason = reason;
        this.status = RefundStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public static Refund create(UUID paymentId, Money amount, String reason) {
        return new Refund(UuidCreator.nextUuidV7(), paymentId, amount, reason);
    }

    public Money getAmount() {
        return new Money(this.amount);
    }

    public void process(String gatewayRefundId) {
        this.status = RefundStatus.PROCESSING;
        this.gatewayRefundId = gatewayRefundId;
    }

    public void complete() {
        this.status = RefundStatus.COMPLETED;
        this.refundedAt = Instant.now();
    }

    public void fail() {
        this.status = RefundStatus.FAILED;
    }
}
