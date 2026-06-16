package com.fooddelivery.payment.domain.model;

import com.fooddelivery.payment.domain.exception.InvalidPaymentStateException;
import com.fooddelivery.payment.domain.exception.RefundExceedsPaymentAmountException;
import com.fooddelivery.payment.domain.model.valueobject.*;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.fooddelivery.payment.domain.util.UuidCreator;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Type(JsonType.class)
    @Column(name = "gateway_response", columnDefinition = "jsonb")
    private GatewayResponse gatewayResponse;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failed_reason")
    private String failedReason;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private List<Refund> refunds = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Payment(UUID id, UUID orderId, UUID customerId, Money amount, PaymentMethod paymentMethod) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount.amount();
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Payment create(UUID orderId, UUID customerId, Money amount, PaymentMethod paymentMethod) {
        return new Payment(UuidCreator.nextUuidV7(), orderId, customerId, amount, paymentMethod);
    }

    public Money getAmount() {
        return new Money(this.amount);
    }

    public void process() {
        if (status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(status);
        }
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markPaid(String gatewayTxnId, GatewayResponse response) {
        if (status != PaymentStatus.PROCESSING) {
            throw new InvalidPaymentStateException(status);
        }
        this.status = PaymentStatus.PAID;
        this.gatewayTransactionId = gatewayTxnId;
        this.gatewayResponse = response;
        this.paidAt = Instant.now();
        this.updatedAt = this.paidAt;
    }

    public void markFailed(String reason) {
        if (status != PaymentStatus.PROCESSING) {
            throw new InvalidPaymentStateException(status);
        }
        this.status = PaymentStatus.FAILED;
        this.failedReason = reason;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == PaymentStatus.PAID || status == PaymentStatus.REFUNDED) {
            throw new InvalidPaymentStateException(status);
        }
        this.status = PaymentStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public Refund requestRefund(Money refundAmount, String reason) {
        if (status != PaymentStatus.PAID) {
            throw new InvalidPaymentStateException(status);
        }
        
        Money alreadyRefunded = refunds.stream()
                .filter(r -> r.getStatus() == RefundStatus.COMPLETED)
                .map(Refund::getAmount)
                .reduce(Money.ZERO, Money::add);
        
        BigDecimal remaining = this.amount.subtract(alreadyRefunded.amount());
        if (refundAmount.amount().compareTo(remaining) > 0) {
            throw new RefundExceedsPaymentAmountException(this.id);
        }
        
        Refund refund = Refund.create(this.id, refundAmount, reason);
        refunds.add(refund);
        
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();
        return refund;
    }
}
