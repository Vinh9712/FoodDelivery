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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

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

    @Column(name = "idempotency_key", nullable = false, length = 200, unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Refund(UUID id, Payment payment, Money amount, String reason,
                  String idempotencyKey, String requestHash) {
        this.id = id;
        this.payment = payment;
        this.amount = amount.amount();
        this.reason = reason;
        this.status = RefundStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = Instant.now();
    }

    public static Refund create(Payment payment, Money amount, String reason,
                                String idempotencyKey, String requestHash) {
        return new Refund(UuidCreator.nextUuidV7(), payment, amount, reason, idempotencyKey, requestHash);
    }

    /** @deprecated use {@link #create(Payment, Money, String, String, String)} */
    @Deprecated
    public static Refund create(Payment payment, Money amount, String reason) {
        String legacyKey = "legacy-refund:" + UuidCreator.nextUuidV7();
        String legacyHash = "0".repeat(64);
        return create(payment, amount, reason, legacyKey, legacyHash);
    }

    public UUID getPaymentId() {
        return payment.getId();
    }

    public Money getAmount() {
        return new Money(this.amount);
    }

    public void process(String gatewayRefundId) {
        this.status = RefundStatus.PROCESSING;
        this.gatewayRefundId = gatewayRefundId;
    }

    /**
     * Completes the refund and marks the owning payment REFUNDED.
     * Only this method (not requestRefund) may move payment to REFUNDED.
     */
    public void complete() {
        if (this.status == RefundStatus.COMPLETED) {
            return;
        }
        this.status = RefundStatus.COMPLETED;
        this.refundedAt = Instant.now();
        payment.markRefunded();
    }

    public void fail() {
        this.status = RefundStatus.FAILED;
    }
}
