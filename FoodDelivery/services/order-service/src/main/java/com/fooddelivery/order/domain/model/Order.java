package com.fooddelivery.order.domain.model;

import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.model.valueobject.*;
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
import java.util.Optional;
import java.util.UUID;
import com.fooddelivery.order.domain.util.UuidCreator;

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

    @Type(JsonType.class)
    @Column(name = "delivery_address_snapshot", columnDefinition = "jsonb")
    private DeliveryAddressSnapshot deliveryAddressSnapshot;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Column(name = "promotion_code", length = 50)
    private String promotionCode;

    @Column(name = "note")
    private String note;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Create a new Order (Legacy constructor preserved for backward compatibility).
     */
    public Order(UUID customerId, UUID restaurantId, BigDecimal totalAmount) {
        this.id = UuidCreator.nextUuidV7();
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.subtotal = totalAmount;
        this.deliveryFee = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.paymentStatus = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Create a rich Order aggregate root with items and details.
     */
    public static Order create(UUID customerId, UUID restaurantId, List<OrderItem> items,
                               DeliveryAddressSnapshot address, Money deliveryFee,
                               Money discountAmount, String promotionCode, String note) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items cannot be empty");
        }
        Order order = new Order();
        order.id = UuidCreator.nextUuidV7();
        order.customerId = customerId;
        order.restaurantId = restaurantId;
        order.status = OrderStatus.PENDING;
        order.deliveryAddressSnapshot = address;
        order.deliveryFee = deliveryFee.amount();
        order.discountAmount = discountAmount.amount();
        order.promotionCode = promotionCode;
        order.note = note;
        order.paymentStatus = PaymentStatus.PENDING;

        BigDecimal sub = BigDecimal.ZERO;
        for (OrderItem item : items) {
            order.items.add(new OrderItem(item.getId(), order.id, item.getMenuItemId(), item.getName(), item.getPrice(), item.getQuantity()));
            sub = sub.add(item.getSubtotal().amount());
        }
        order.subtotal = sub;
        order.totalAmount = sub.add(order.deliveryFee).subtract(order.discountAmount);
        if (order.totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            order.totalAmount = BigDecimal.ZERO;
        }

        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        order.statusHistory.add(OrderStatusHistory.of(order.id, null, OrderStatus.PENDING, "Order placed", customerId));
        return order;
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
            return;
        }
        this.assignedDriverSnapshot = driverInfo;
        this.updatedAt = Instant.now();
    }

    public Optional<AssignedDriverInfo> getAssignedDriver() {
        return Optional.ofNullable(assignedDriverSnapshot);
    }

    public Money getSubtotalMoney() {
        return new Money(this.subtotal);
    }

    public Money getDeliveryFeeMoney() {
        return new Money(this.deliveryFee);
    }

    public Money getDiscountMoney() {
        return new Money(this.discountAmount);
    }

    public Money getTotalMoney() {
        return new Money(this.totalAmount);
    }

    /**
     * Confirm a pending order.
     */
    public void confirm() {
        transition(OrderStatus.PENDING, OrderStatus.CONFIRMED, "Order confirmed", null);
    }

    public void startPreparing() {
        transition(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "Start preparing food", null);
    }

    public void markReady() {
        transition(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, "Food is ready for pick up", null);
    }

    public void pickUp() {
        transition(OrderStatus.READY_FOR_PICKUP, OrderStatus.PICKED_UP, "Driver picked up food", null);
    }

    public void startDelivering() {
        transition(OrderStatus.PICKED_UP, OrderStatus.DELIVERING, "Order is on the way", null);
    }

    public void complete() {
        transition(OrderStatus.DELIVERING, OrderStatus.DELIVERED, "Order delivered successfully", null);
    }

    /**
     * Cancel the order.
     */
    public void cancel(String reason, UUID cancelledBy) {
        if (this.status == OrderStatus.DELIVERED || this.status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Cannot cancel order in status: " + this.status);
        }
        OrderStatus from = this.status;
        this.status = OrderStatus.CANCELLED;
        statusHistory.add(OrderStatusHistory.of(this.id, from, OrderStatus.CANCELLED, reason, cancelledBy));
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        cancel("Order cancelled", null);
    }

    public void updatePaymentStatus(PaymentStatus newStatus) {
        this.paymentStatus = newStatus;
        if (newStatus == PaymentStatus.FAILED && this.status == OrderStatus.PENDING) {
            cancel("Payment failed", null);
        }
        this.updatedAt = Instant.now();
    }

    private void transition(OrderStatus expected, OrderStatus next, String note, UUID changedBy) {
        if (this.status != expected) {
            throw new InvalidOrderStateException(
                    "Expected status " + expected + " but was " + this.status);
        }
        statusHistory.add(OrderStatusHistory.of(this.id, expected, next, note, changedBy));
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
