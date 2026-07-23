package com.fooddelivery.order.domain.model;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.model.valueobject.*;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Duration;
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
 *      │       │         │                        │               │            │
 *      │       └→ CANCELLATION_PENDING ←──────────┴───────────────┴────────────┘
 *      │                    │
 *      └→ CANCELLED ←───────┘  (payment fail, or refund confirmed)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 30)
    private RefundStatus refundStatus;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "restaurant_response_deadline")
    private Instant restaurantResponseDeadline;

    @Type(JsonType.class)
    @Column(name = "pickup_address_snapshot", columnDefinition = "jsonb")
    private PickupAddressSnapshot pickupAddressSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_code", length = 50)
    private CancellationCode cancellationCode;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;

    /** Delivery aggregate id observed via schedule response or reconciliation lookup. */
    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "delivery_schedule_attempts", nullable = false)
    private int deliveryScheduleAttempts;

    @Column(name = "next_delivery_schedule_attempt_at")
    private Instant nextDeliveryScheduleAttemptAt;

    @Column(name = "last_delivery_schedule_error", length = 1000)
    private String lastDeliveryScheduleError;

    @Column(name = "refund_attempts", nullable = false)
    private int refundAttempts;

    @Column(name = "next_refund_attempt_at")
    private Instant nextRefundAttemptAt;

    @Column(name = "last_refund_error", length = 1000)
    private String lastRefundError;

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
        return create(customerId, restaurantId, deliveryAddressJson, deliveryFee, discountAmount,
                clientRequestId, null);
    }

    public static Order create(UUID customerId, UUID restaurantId,
                               String deliveryAddressJson,
                               BigDecimal deliveryFee, BigDecimal discountAmount,
                               String clientRequestId, PickupAddressSnapshot pickupAddressSnapshot) {
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
        order.refundStatus = RefundStatus.NOT_REQUIRED;
        order.deliveryScheduleAttempts = 0;
        order.refundAttempts = 0;
        order.pickupAddressSnapshot = pickupAddressSnapshot;
        if (pickupAddressSnapshot != null && !restaurantId.equals(pickupAddressSnapshot.restaurantId())) {
            throw new IllegalArgumentException("pickup snapshot restaurantId must match order restaurantId");
        }
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
        order.refundStatus = RefundStatus.NOT_REQUIRED;
        order.deliveryScheduleAttempts = 0;
        order.refundAttempts = 0;

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
        this.refundStatus = RefundStatus.NOT_REQUIRED;
        this.deliveryScheduleAttempts = 0;
        this.refundAttempts = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }


    public void applyNote(String note) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Note can only be set while PENDING");
        }
        if (note == null || note.isBlank()) {
            this.note = null;
            return;
        }
        String trimmed = note.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Note cannot exceed 500 characters");
        }
        this.note = trimmed;
        this.updatedAt = Instant.now();
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

    public void markPaid(Instant paidAt, Duration restaurantAcceptanceTimeout) {
        Objects.requireNonNull(paidAt, "paidAt is required");
        Objects.requireNonNull(restaurantAcceptanceTimeout, "restaurantAcceptanceTimeout is required");
        if (restaurantAcceptanceTimeout.isNegative()) {
            throw new IllegalArgumentException("restaurantAcceptanceTimeout must not be negative");
        }
        if (!forwardTransition(OrderStatus.PENDING, OrderStatus.PAID, "Payment captured", null,
                paidAt, OrderEventPayloads.Source.PAYMENT_RECONCILIATION)) {
            return;
        }
        this.paymentStatus = PaymentStatus.PAID;
        this.paidAt = paidAt;
        this.restaurantResponseDeadline = paidAt.plus(restaurantAcceptanceTimeout);
    }

    /** @deprecated use {@link #markPaid(Instant, Duration)} with an explicit clock. */
    @Deprecated
    public void markAsPaid() {
        markPaid(Instant.now(), Duration.ofMinutes(10));
    }

    public void markPaymentFailed(String reason, Instant failedAt) {
        Objects.requireNonNull(failedAt, "failedAt is required");
        String normalizedReason = normalizedReason(reason);
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Payment failure is only valid for PENDING orders; was " + status);
        }
        if (!forwardTransition(OrderStatus.PENDING, OrderStatus.CANCELLED, normalizedReason, null,
                failedAt, OrderEventPayloads.Source.PAYMENT_RECONCILIATION)) {
            return;
        }
        this.paymentStatus = PaymentStatus.FAILED;
        this.refundStatus = RefundStatus.NOT_REQUIRED;
        this.cancellationReason = normalizedReason;
        registerCancellationEvent(failedAt);
    }

    public void acceptByRestaurant(UUID acceptedBy) {
        forwardTransition(OrderStatus.PAID, OrderStatus.CONFIRMED, "Restaurant accepted order", acceptedBy,
                Instant.now(), OrderEventPayloads.Source.RESTAURANT);
    }

    public void startPreparing(UUID changedBy) {
        forwardTransition(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "Restaurant started preparing", changedBy,
                Instant.now(), OrderEventPayloads.Source.RESTAURANT);
    }

    public void markReadyForPickup(UUID changedBy) {
        forwardTransition(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, "Order ready for pickup", changedBy,
                Instant.now(), OrderEventPayloads.Source.RESTAURANT);
    }

    public void markPickedUp(Instant pickedUpAt, OrderEventPayloads.Source source) {
        requireDeliverySource(source);
        forwardTransition(OrderStatus.READY_FOR_PICKUP, OrderStatus.PICKED_UP, "Order picked up", null,
                Objects.requireNonNull(pickedUpAt, "pickedUpAt is required"), source);
    }

    public void markDelivering(Instant deliveryStartedAt, OrderEventPayloads.Source source) {
        requireDeliverySource(source);
        forwardTransition(OrderStatus.PICKED_UP, OrderStatus.DELIVERING, "Order delivering", null,
                Objects.requireNonNull(deliveryStartedAt, "deliveryStartedAt is required"), source);
    }

    public void markDelivered(Instant deliveredAt, OrderEventPayloads.Source source) {
        requireDeliverySource(source);
        forwardTransition(OrderStatus.DELIVERING, OrderStatus.DELIVERED, "Order delivered", null,
                Objects.requireNonNull(deliveredAt, "deliveredAt is required"), source);
    }

    public void requestCancellation(String reason, CancellationCode code, OrderEventPayloads.Source source) {
        beginCompensation(reason, code, source, Instant.now());
    }

    /**
     * Eligibility for restaurant acceptance timeout: still {@code PAID} and deadline has elapsed.
     * Does not mutate state — caller must invoke compensation when this returns true.
     * Uses {@code deadline <= now} (deadline equal to now is overdue).
     */
    public boolean requestCancellationIfRestaurantTimedOut(Instant now) {
        Objects.requireNonNull(now, "now is required");
        return status == OrderStatus.PAID
                && restaurantResponseDeadline != null
                && !now.isBefore(restaurantResponseDeadline);
    }

    public void markRefundSucceeded(Instant refundedAt) {
        confirmRefund(null, null, null, refundedAt);
    }

    /**
     * Confirms a completed remote refund. Idempotent for already-cancelled/refunded orders.
     * Emits {@code OrderRefundStatusChanged} then {@code OrderCancelled} once.
     */
    public void confirmRefund(UUID paymentId, UUID refundId, BigDecimal amount, Instant refundedAt) {
        Objects.requireNonNull(refundedAt, "refundedAt is required");
        if (status == OrderStatus.CANCELLED
                && paymentStatus == PaymentStatus.REFUNDED
                && refundStatus == RefundStatus.SUCCEEDED) {
            return;
        }
        if (status != OrderStatus.CANCELLATION_PENDING && status != OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(
                    "Refund confirmation requires CANCELLATION_PENDING; was " + status);
        }
        RefundStatus fromRefund = this.refundStatus;
        if (fromRefund != RefundStatus.SUCCEEDED) {
            this.refundStatus = RefundStatus.SUCCEEDED;
            registerOutboxEvent("OrderRefundStatusChanged", Map.of(
                    "orderId", this.id.toString(),
                    "customerId", this.customerId.toString(),
                    "fromRefundStatus", fromRefund.name(),
                    "toRefundStatus", RefundStatus.SUCCEEDED.name(),
                    "reason", cancellationReason != null ? cancellationReason : "Refund confirmed",
                    "changedAt", refundedAt.toString()));
        }
        this.paymentStatus = PaymentStatus.REFUNDED;
        this.nextRefundAttemptAt = null;
        this.lastRefundError = null;
        if (status == OrderStatus.CANCELLATION_PENDING) {
            if (!forwardTransition(OrderStatus.CANCELLATION_PENDING, OrderStatus.CANCELLED, "Refund succeeded", null,
                    refundedAt, OrderEventPayloads.Source.COMPENSATION)) {
                return;
            }
            registerCancellationEvent(refundedAt);
        }
    }

    /**
     * Attach observed delivery id without changing fulfillment status.
     * Clears schedule-retry metadata after positive remote observation.
     */
    public void attachDelivery(UUID observedDeliveryId) {
        this.deliveryId = Objects.requireNonNull(observedDeliveryId, "deliveryId is required");
        clearDeliveryScheduleRetryMetadata();
        this.updatedAt = Instant.now();
    }

    public void recordDeliveryScheduleFailure(String error, Instant nextAttemptAt, Instant failedAt) {
        this.deliveryScheduleAttempts++;
        this.lastDeliveryScheduleError = truncateError(error);
        this.nextDeliveryScheduleAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt is required");
        this.updatedAt = Objects.requireNonNull(failedAt, "failedAt is required");
    }

    public void clearDeliveryScheduleRetryMetadata() {
        this.deliveryScheduleAttempts = 0;
        this.nextDeliveryScheduleAttemptAt = null;
        this.lastDeliveryScheduleError = null;
    }

    public void scheduleFirstRefundAttempt(Instant nextAttemptAt) {
        this.refundAttempts = 0;
        this.nextRefundAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt is required");
        this.lastRefundError = null;
    }

    public void recordRefundAttemptFailure(String error, Instant nextAttemptAt, Instant failedAt) {
        this.refundAttempts++;
        this.lastRefundError = truncateError(error);
        this.nextRefundAttemptAt = nextAttemptAt;
        this.updatedAt = Objects.requireNonNull(failedAt, "failedAt is required");
    }

    public void markRefundManualReview(String reason, Instant changedAt) {
        if (this.refundStatus == RefundStatus.MANUAL_REVIEW) {
            return;
        }
        if (this.refundStatus != RefundStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Manual review is only valid from PENDING refund status; was " + this.refundStatus);
        }
        this.refundStatus = RefundStatus.MANUAL_REVIEW;
        this.nextRefundAttemptAt = null;
        this.lastRefundError = truncateError(reason);
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt is required");
        registerOutboxEvent("OrderRefundStatusChanged", Map.of(
                "orderId", this.id.toString(),
                "customerId", this.customerId.toString(),
                "fromRefundStatus", RefundStatus.PENDING.name(),
                "toRefundStatus", RefundStatus.MANUAL_REVIEW.name(),
                "reason", reason != null ? reason : "Refund retry exhausted",
                "changedAt", changedAt.toString()));
    }

    /**
     * Begins compensation: CANCELLATION_PENDING + RefundStatus.PENDING.
     * Never sets PaymentStatus.REFUNDED. Idempotent when already pending.
     */
    public void beginCompensation(String reason, CancellationCode code, OrderEventPayloads.Source source,
                                  Instant changedAt) {
        Objects.requireNonNull(changedAt, "changedAt is required");
        if (status == OrderStatus.CANCELLATION_PENDING) {
            return;
        }
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        String normalizedReason = normalizedReason(reason);
        Objects.requireNonNull(code, "cancellationCode is required");
        requireCancellationSource(code, source);
        if (!isCancellationAllowed(status, code)) {
            throw new InvalidOrderStateException(
                    "Cannot request cancellation with code " + code + " in status: " + status);
        }
        OrderStatus from = this.status;
        if (!forwardTransition(from, OrderStatus.CANCELLATION_PENDING, normalizedReason, null, changedAt, source)) {
            return;
        }
        this.cancellationCode = code;
        this.cancellationReason = normalizedReason;
        RefundStatus fromRefund = this.refundStatus;
        this.refundStatus = RefundStatus.PENDING;
        if (fromRefund != RefundStatus.PENDING) {
            registerOutboxEvent("OrderRefundStatusChanged", Map.of(
                    "orderId", this.id.toString(),
                    "customerId", this.customerId.toString(),
                    "fromRefundStatus", fromRefund.name(),
                    "toRefundStatus", RefundStatus.PENDING.name(),
                    "reason", normalizedReason,
                    "changedAt", changedAt.toString()));
        }
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
        UUID requiredDriverId = Objects.requireNonNull(driverId, "driverId must not be null");
        if (requiredDriverId.equals(this.driverId)) {
            return;
        }
        this.driverId = requiredDriverId;
        this.updatedAt = Instant.now();
    }

    public void assignDriver(AssignedDriverInfo driverInfo) {
        if (this.status == OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Cannot assign driver before order is confirmed. Current status: " + this.status);
        }
        if (this.status == OrderStatus.DELIVERED || this.status == OrderStatus.CANCELLED) {
            return;
        }
        AssignedDriverInfo requiredDriverInfo = Objects.requireNonNull(driverInfo, "driverInfo must not be null");
        if (requiredDriverInfo.equals(this.assignedDriverSnapshot)) {
            return;
        }
        this.assignedDriverSnapshot = requiredDriverInfo;
        this.driverId = requiredDriverInfo.driverId();
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
        cancel(reason, null);
    }

    /**
     * Hủy đơn hàng với lý do và người hủy (backward compatible).
     */
    public void cancel(String reason, UUID cancelledBy) {
        if (this.status == OrderStatus.PENDING) {
            markPaymentFailed(reason, Instant.now());
            return;
        }
        if (status == OrderStatus.PAID || status == OrderStatus.CONFIRMED) {
            requestCancellation(reason, CancellationCode.RESTAURANT_REJECTED, OrderEventPayloads.Source.RESTAURANT);
            return;
        }
        requestCancellation(reason, CancellationCode.DELIVERY_FAILED, OrderEventPayloads.Source.DELIVERY_EVENT);
    }

    public void cancel() {
        cancel("Order cancelled", null);
    }

    // ── Các transition lifecycle khác (backward compatible) ──

    public void confirm() {
        acceptByRestaurant(null);
    }

    public void startPreparing() {
        startPreparing(null);
    }

    public void markReady() {
        markReadyForPickup(null);
    }

    public void pickUp() {
        markPickedUp(Instant.now(), OrderEventPayloads.Source.DELIVERY_EVENT);
    }

    public void startDelivering() {
        markDelivering(Instant.now(), OrderEventPayloads.Source.DELIVERY_EVENT);
    }

    public void complete() {
        markDelivered(Instant.now(), OrderEventPayloads.Source.DELIVERY_EVENT);
    }

    public void updatePaymentStatus(PaymentStatus newStatus) {
        if (newStatus == PaymentStatus.FAILED) {
            markPaymentFailed("Payment failed", Instant.now());
            return;
        }
        this.paymentStatus = Objects.requireNonNull(newStatus, "paymentStatus is required");
        this.updatedAt = Instant.now();
    }


    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getClientRequestId() { return clientRequestId; }
    public long getVersion() { return version; }
    public UUID getDriverId() { return driverId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public AssignedDriverInfo getAssignedDriverSnapshot() { return assignedDriverSnapshot; }
    public DeliveryAddressSnapshot getDeliveryAddressSnapshot() { return deliveryAddressSnapshot; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public RefundStatus getRefundStatus() { return refundStatus; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getRestaurantResponseDeadline() { return restaurantResponseDeadline; }
    public PickupAddressSnapshot getPickupAddressSnapshot() { return pickupAddressSnapshot; }
    public CancellationCode getCancellationCode() { return cancellationCode; }
    public String getCancellationReason() { return cancellationReason; }
    public long getEventSequence() { return eventSequence; }
    public UUID getDeliveryId() { return deliveryId; }
    public int getDeliveryScheduleAttempts() { return deliveryScheduleAttempts; }
    public Instant getNextDeliveryScheduleAttemptAt() { return nextDeliveryScheduleAttemptAt; }
    public String getLastDeliveryScheduleError() { return lastDeliveryScheduleError; }
    public int getRefundAttempts() { return refundAttempts; }
    public Instant getNextRefundAttemptAt() { return nextRefundAttemptAt; }
    public String getLastRefundError() { return lastRefundError; }
    public String getPromotionCode() { return promotionCode; }
    public String getNote() { return note; }
    public List<OrderItem> getItems() { return items; }
    public List<OrderStatusHistory> getStatusHistory() { return statusHistory; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

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

    private boolean forwardTransition(OrderStatus expected, OrderStatus next, String note, UUID changedBy,
                                      Instant changedAt, OrderEventPayloads.Source source) {
        if (this.status == next) {
            return false;
        }
        if (this.status != expected) {
            throw new InvalidOrderStateException(
                    "Expected status " + expected + " but was " + this.status);
        }
        statusHistory.add(OrderStatusHistory.of(this.id, expected, next, note, changedBy));
        this.status = next;
        this.updatedAt = changedAt;
        registerOutboxEvent("OrderStatusChanged", Map.of(
                "orderId", this.id.toString(),
                "customerId", this.customerId.toString(),
                "restaurantId", this.restaurantId.toString(),
                "fromStatus", expected.name(),
                "toStatus", next.name(),
                "source", source.name(),
                "changedAt", changedAt.toString()));
        return true;
    }

    private void registerCancellationEvent(Instant cancelledAt) {
        registerOutboxEvent("OrderCancelled", Map.of(
                "orderId", this.id.toString(),
                "customerId", this.customerId.toString(),
                "restaurantId", this.restaurantId.toString(),
                "cancellationCode", cancellationCode == null ? "PAYMENT_FAILED" : cancellationCode.name(),
                "reason", cancellationReason == null ? "Payment failed" : cancellationReason,
                "paymentStatus", this.paymentStatus.name(),
                "refundStatus", this.refundStatus.name(),
                "cancelledAt", cancelledAt.toString()));
    }

    private String normalizedReason(String reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reason is required");
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > 500) {
            throw new IllegalArgumentException("reason must contain 1 to 500 characters");
        }
        return trimmed;
    }

    private void requireDeliverySource(OrderEventPayloads.Source source) {
        if (source != OrderEventPayloads.Source.DELIVERY_EVENT
                && source != OrderEventPayloads.Source.DELIVERY_RECONCILIATION) {
            throw new IllegalArgumentException("Delivery transitions require a delivery source");
        }
    }

    private void requireCancellationSource(CancellationCode code, OrderEventPayloads.Source source) {
        boolean valid = switch (code) {
            case CUSTOMER_REQUESTED -> source == OrderEventPayloads.Source.CUSTOMER;
            case RESTAURANT_REJECTED -> source == OrderEventPayloads.Source.RESTAURANT;
            case RESTAURANT_ACCEPTANCE_TIMEOUT -> source == OrderEventPayloads.Source.SYSTEM_TIMEOUT;
            case DELIVERY_FAILED -> source == OrderEventPayloads.Source.DELIVERY_EVENT
                    || source == OrderEventPayloads.Source.DELIVERY_RECONCILIATION;
            case ADMIN_CANCELLED -> source == OrderEventPayloads.Source.ADMIN;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid source for cancellation code: " + code);
        }
    }

    /**
     * Allowed cancel edges from the fulfillment design:
     * PAID/CONFIRMED for restaurant reject or acceptance timeout (timeout only from PAID);
     * READY_FOR_PICKUP/PICKED_UP/DELIVERING for delivery failure;
     * customer: PAID/CONFIRMED (PENDING handled via {@link #cancelUnpaid});
     * admin: broader pre-delivery and in-transit.
     */
    private static boolean isCancellationAllowed(OrderStatus current, CancellationCode code) {
        return switch (code) {
            case RESTAURANT_REJECTED -> current == OrderStatus.PAID
                    || current == OrderStatus.CONFIRMED
                    || current == OrderStatus.PREPARING;
            case RESTAURANT_ACCEPTANCE_TIMEOUT -> current == OrderStatus.PAID;
            case DELIVERY_FAILED -> current == OrderStatus.READY_FOR_PICKUP
                    || current == OrderStatus.PICKED_UP
                    || current == OrderStatus.DELIVERING;
            case CUSTOMER_REQUESTED -> current == OrderStatus.PAID || current == OrderStatus.CONFIRMED;
            case ADMIN_CANCELLED -> current == OrderStatus.PAID
                    || current == OrderStatus.CONFIRMED
                    || current == OrderStatus.PREPARING
                    || current == OrderStatus.READY_FOR_PICKUP
                    || current == OrderStatus.PICKED_UP
                    || current == OrderStatus.DELIVERING;
        };
    }

    /**
     * Unpaid order cancel (PENDING): go straight to CANCELLED without refund.
     */
    public void cancelUnpaid(String reason, CancellationCode code, OrderEventPayloads.Source source, Instant cancelledAt) {
        Objects.requireNonNull(cancelledAt, "cancelledAt is required");
        Objects.requireNonNull(code, "cancellationCode is required");
        requireCancellationSource(code, source);
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Unpaid cancel requires PENDING; was " + status);
        }
        String normalizedReason = normalizedReason(reason);
        if (!forwardTransition(OrderStatus.PENDING, OrderStatus.CANCELLED, normalizedReason, null,
                cancelledAt, source)) {
            return;
        }
        this.cancellationCode = code;
        this.cancellationReason = normalizedReason;
        this.paymentStatus = PaymentStatus.FAILED;
        this.refundStatus = RefundStatus.NOT_REQUIRED;
        registerCancellationEvent(cancelledAt);
    }

    /** Đăng ký outbox event vào danh sách chờ */
    private void registerOutboxEvent(String eventType, Map<String, Object> payload) {
        long sequence = nextEventSequence();
        pendingOutboxEvents.add(OutboxEvent.create(
                "Order", this.id, eventType, 1, sequence, this.id.toString(), payload));
    }

    /**
     * Allocates the next monotonic outbox sequence for this order aggregate.
     * Sequence is owned by the aggregate transaction — never by the relay.
     */
    private long nextEventSequence() {
        return ++eventSequence;
    }

    private static String truncateError(String error) {
        if (error == null || error.isBlank()) {
            return "unknown";
        }
        String trimmed = error.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }
}
