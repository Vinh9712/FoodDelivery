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
import java.util.*;

import com.fooddelivery.order.domain.util.UuidCreator;

/**
 * Order Aggregate Root — tuân thủ DDD Rich Domain Model.
 * <p>
 * Tất cả thay đổi trạng thái được bảo vệ bởi State Machine Invariants.
 * Mỗi transition tự động ghi OrderStatusHistory và tạo OutboxEvent
 * để đảm bảo Transactional Outbox Pattern.
 * </p>
 *
 * <h3>State Machine:</h3>
 * <pre>
 *   PENDING → PAID → CONFIRMED → PREPARING → READY_FOR_PICKUP → PICKED_UP → DELIVERING → DELIVERED
 *       │        │
 *       └→ CANCELLED ←┘  (chỉ hủy từ PENDING hoặc PAID)
 * </pre>
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

    @Column(name = "client_request_id", length = 100)
    private String clientRequestId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Driver ID — null trước khi assign, set sau khi Delivery Service trả về */
    @Column(name = "driver_id")
    private UUID driverId;

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

    // ── Outbox Events (không persist trực tiếp, lưu qua OutboxEventRepository) ──

    /**
     * Danh sách event chờ lưu vào bảng outbox_events trong cùng transaction.
     * Controller/Service layer sẽ đọc list này và persist qua OutboxEventRepository.
     */
    @Transient
    private final List<OutboxEvent> pendingOutboxEvents = new ArrayList<>();

    // ══════════════════════════════════════════════════════════════════════
    // FACTORY METHODS — Không có public constructor (trừ legacy)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Factory method tạo Order mới cho Saga flow (NHIỆM VỤ 1).
     * <p>
     * Items sẽ được thêm sau qua {@link #addItem(UUID, String, String, BigDecimal, int)}.
     * </p>
     *
     * @param customerId          ID khách hàng
     * @param restaurantId        ID nhà hàng
     * @param deliveryAddressJson Địa chỉ giao hàng dạng JSON string (sẽ parse thành DeliveryAddressSnapshot)
     * @param deliveryFee         Phí giao hàng
     * @param discountAmount      Số tiền giảm giá
     * @return Order mới ở trạng thái PENDING
     */
    public static Order create(UUID customerId, UUID restaurantId,
                               String deliveryAddressJson,
                               BigDecimal deliveryFee, BigDecimal discountAmount) {
        return create(customerId, restaurantId, deliveryAddressJson, deliveryFee, discountAmount, null);
    }

    public static Order create(UUID customerId, UUID restaurantId,
                               String deliveryAddressJson,
                               BigDecimal deliveryFee, BigDecimal discountAmount,
                               String clientRequestId) {
        var order = new Order();
        order.id = UuidCreator.nextUuidV7();
        order.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        order.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId must not be null");
        order.clientRequestId = clientRequestId;
        order.status = OrderStatus.PENDING;

        // Parse JSON thành DeliveryAddressSnapshot — lưu nguyên chuỗi vào addressLine
        // để tương thích với JSONB column. Nếu cần parse chi tiết, mở rộng tại đây.
        order.deliveryAddressSnapshot = new DeliveryAddressSnapshot(
                deliveryAddressJson != null ? deliveryAddressJson : "N/A",
                "N/A", "N/A", null, null
        );

        order.deliveryFee = deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
        order.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        order.subtotal = BigDecimal.ZERO;
        order.totalAmount = BigDecimal.ZERO;
        order.paymentStatus = PaymentStatus.PENDING;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;

        // Ghi lịch sử trạng thái ban đầu
        order.statusHistory.add(
                OrderStatusHistory.of(order.id, null, OrderStatus.PENDING, "Đơn hàng được tạo", customerId));

        // Ghi outbox event
        order.registerOutboxEvent("OrderCreated", Map.of(
                "orderId", order.id.toString(),
                "customerId", customerId.toString(),
                "restaurantId", restaurantId.toString(),
                "deliveryAddress", order.deliveryAddressSnapshot.addressLine(),
                "status", "PENDING"
        ));

        return order;
    }

    /**
     * Factory method tạo Order giàu nghiệp vụ — giữ backward compatibility với code hiện có.
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
            order.items.add(new OrderItem(item.getId(), order.id, item.getMenuItemId(),
                    item.getItemName(), item.getUnitPriceMoney(), item.getQuantity()));
            sub = sub.add(item.getSubtotalMoney().amount());
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


    public void addItem(UUID menuItemId, String itemName, String description,
                        BigDecimal unitPrice, int quantity) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ được thêm item khi đơn hàng ở trạng thái PENDING. Trạng thái hiện tại: " + this.status);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng phải lớn hơn 0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Đơn giá không hợp lệ");
        }

        BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        var item = OrderItem.createForSaga(
                this.id, menuItemId, itemName, description, unitPrice, quantity, itemSubtotal);
        this.items.add(item);

        // Cộng dồn subtotal
        this.subtotal = this.subtotal.add(itemSubtotal);

        // Tính lại totalAmount = subtotal + delivery_fee - discount_amount
        recalculateTotal();
        this.updatedAt = Instant.now();
    }

    public void markAsPaid() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ đơn hàng PENDING mới có thể chuyển sang CONFIRMED. Trạng thái hiện tại: " + this.status);
        }
        this.paymentStatus = PaymentStatus.PAID;
        transitionWithOutbox(OrderStatus.PENDING, OrderStatus.CONFIRMED, "Thanh toán thành công", null);
    }


    public void assignDriver(UUID driverId) {
        if (this.status == OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Không thể gán tài xế khi đơn hàng chưa thanh toán. Trạng thái hiện tại: " + this.status);
        }
        if (this.status == OrderStatus.DELIVERED || this.status == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Không thể gán tài xế cho đơn hàng đã hoàn thành/hủy. Trạng thái: " + this.status);
        }
        this.driverId = Objects.requireNonNull(driverId, "driverId must not be null");
        this.updatedAt = Instant.now();

        // Ghi outbox event
        registerOutboxEvent("DriverAssigned", Map.of(
                "orderId", this.id.toString(),
                "driverId", driverId.toString()
        ));
    }

    public void assignDriver(AssignedDriverInfo driverInfo) {
        if (this.status == OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Cannot assign driver before order is confirmed. Current status: " + this.status);
        }
        if (this.status == OrderStatus.DELIVERED || this.status == OrderStatus.CANCELLED) {
            return;
        }
        this.assignedDriverSnapshot = Objects.requireNonNull(driverInfo, "driverInfo must not be null");
        this.driverId = driverInfo.driverId();
        this.updatedAt = Instant.now();
    }

    /**
     * Hủy đơn hàng (Saga compensating transaction).
     * <p>
     * Chỉ cho phép chuyển đổi từ PENDING hoặc PAID.
     * Ghi nhận lý do hủy vào lịch sử trạng thái và tạo outbox event.
     * </p>
     *
     * @param reason lý do hủy đơn
     * @throws IllegalStateException nếu đơn hàng không thể hủy
     */
    public void cancel(String reason) {
        if (this.status != OrderStatus.PENDING && this.status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Chỉ hủy đơn hàng ở trạng thái PENDING hoặc CONFIRMED. Trạng thái hiện tại: " + this.status);
        }
        OrderStatus from = this.status;
        this.status = OrderStatus.CANCELLED;
        this.statusHistory.add(
                OrderStatusHistory.of(this.id, from, OrderStatus.CANCELLED, reason, null));
        this.updatedAt = Instant.now();

        // Ghi outbox event
        registerOutboxEvent("OrderCancelled", Map.of(
                "orderId", this.id.toString(),
                "reason", reason != null ? reason : "Không rõ lý do",
                "fromStatus", from.name()
        ));
    }

    /**
     * Hủy đơn hàng với lý do và người hủy (backward compatible).
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

    // ── Các transition lifecycle khác (backward compatible) ──

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

    public void updatePaymentStatus(PaymentStatus newStatus) {
        this.paymentStatus = newStatus;
        if (newStatus == PaymentStatus.FAILED && this.status == OrderStatus.PENDING) {
            cancel("Payment failed", null);
        }
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


    public String getDeliveryAddressJson() {
        if (deliveryAddressSnapshot == null) return null;
        return deliveryAddressSnapshot.addressLine();
    }


    public List<OutboxEvent> getPendingOutboxEvents() {
        return Collections.unmodifiableList(pendingOutboxEvents);
    }

    public void clearPendingOutboxEvents() {
        pendingOutboxEvents.clear();
    }


    /** Tính lại totalAmount = subtotal + deliveryFee - discountAmount */
    private void recalculateTotal() {
        this.totalAmount = this.subtotal.add(this.deliveryFee).subtract(this.discountAmount);
        if (this.totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            this.totalAmount = BigDecimal.ZERO;
        }
    }

    /** Transition cơ bản (backward compatible) */
    private void transition(OrderStatus expected, OrderStatus next, String note, UUID changedBy) {
        if (this.status != expected) {
            throw new InvalidOrderStateException(
                    "Expected status " + expected + " but was " + this.status);
        }
        statusHistory.add(OrderStatusHistory.of(this.id, expected, next, note, changedBy));
        this.status = next;
        this.updatedAt = Instant.now();
    }

    /** Transition kèm ghi outbox event */
    private void transitionWithOutbox(OrderStatus expected, OrderStatus next,
                                      String note, UUID changedBy) {
        transition(expected, next, note, changedBy);
        registerOutboxEvent("OrderStatusChanged", Map.of(
                "orderId", this.id.toString(),
                "fromStatus", expected.name(),
                "toStatus", next.name(),
                "note", note != null ? note : ""
        ));
    }

    /** Đăng ký outbox event vào danh sách chờ */
    private void registerOutboxEvent(String eventType, Map<String, Object> payload) {
        pendingOutboxEvents.add(
                OutboxEvent.create("Order", this.id, eventType, payload));
    }
}
