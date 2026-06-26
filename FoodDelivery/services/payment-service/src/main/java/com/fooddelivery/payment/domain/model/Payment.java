package com.fooddelivery.payment.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a payment transaction.
 *
 * <p>One Payment record is created per order. The service auto-processes
 * it (simulates a payment gateway) and publishes {@code payment.processed}
 * or {@code payment.failed} events to Kafka.</p>
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor          // required by JPA
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The order this payment is for — unique constraint enforces one payment per order. */
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    /** Amount charged for this order. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Currency code, e.g. VND or USD. */
    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** Optional failure message when status == FAILED. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Timestamp when this payment was first created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Timestamp of the last status change. */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onPersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.updatedAt == null) this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
