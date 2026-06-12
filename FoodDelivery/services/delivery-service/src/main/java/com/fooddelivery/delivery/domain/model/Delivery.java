package com.fooddelivery.delivery.domain.model;

import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivery aggregate root — tracks the lifecycle of a delivery for an order.
 */
@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Create a new Delivery for an order.
     */
    public Delivery(UUID orderId) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Assign a driver to this delivery.
     */
    public void assignDriver(UUID driverId) {
        this.driverId = driverId;
        this.status = DeliveryStatus.DRIVER_ASSIGNED;
        this.updatedAt = Instant.now();
    }
}
