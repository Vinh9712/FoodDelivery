package com.fooddelivery.order.domain.model;

import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.model.valueobject.AssignedDriverInfo;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Order aggregate root.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Type(JsonType.class)
    @Column(name = "assigned_driver_snapshot", columnDefinition = "jsonb")
    private AssignedDriverInfo assignedDriverSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Create a new Order.
     */
    public Order(UUID customerId, UUID restaurantId, BigDecimal totalAmount) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Called when consuming {@code driver.assigned} event.
     * Allows re-assignment (driver change) unless order is DELIVERED/CANCELLED.
     */
    public void assignDriver(AssignedDriverInfo driverInfo) {
        if (this.status == OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Cannot assign driver before order is confirmed. Current status: " + this.status);
        }
        if (this.status == OrderStatus.DELIVERED || this.status == OrderStatus.CANCELLED) {
            // Late/out-of-order event — ignore silently
            return;
        }
        this.assignedDriverSnapshot = driverInfo;
        this.updatedAt = Instant.now();
    }

    public Optional<AssignedDriverInfo> getAssignedDriver() {
        return Optional.ofNullable(assignedDriverSnapshot);
    }

    /**
     * Confirm a pending order.
     */
    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Can only confirm PENDING orders. Current status: " + this.status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    /**
     * Cancel the order.
     */
    public void cancel() {
        if (this.status == OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException("Cannot cancel a DELIVERED order.");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
