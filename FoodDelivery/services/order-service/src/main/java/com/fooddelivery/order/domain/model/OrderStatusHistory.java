package com.fooddelivery.order.domain.model;

import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.order.domain.util.UuidCreator;

@Entity
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private OrderStatus toStatus;

    @Column(name = "note")
    private String note;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OrderStatusHistory(UUID id, UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, String note, UUID changedBy) {
        this.id = id;
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.changedBy = changedBy;
        this.createdAt = Instant.now();
    }

    public static OrderStatusHistory of(UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, String note, UUID changedBy) {
        return new OrderStatusHistory(UuidCreator.nextUuidV7(), orderId, fromStatus, toStatus, note, changedBy);
    }
}
