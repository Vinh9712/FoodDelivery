package com.fooddelivery.order.saga;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.exception.InvalidOrderRequestException;
import com.fooddelivery.order.domain.exception.OrderDependencyException;
import com.fooddelivery.order.infrastructure.client.*;
import com.fooddelivery.order.infrastructure.client.dto.*;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import feign.FeignException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saga Orchestrator điều phối luồng đặt hàng phân tán.
 * <p>
 * Luồng chính:
 * <pre>
 *   1. Tạo Order (PENDING) → save vào DB
 *   2. Gọi Payment Service
 *      ├─ SUCCESS → markAsPaid() → gọi Delivery Service
 *      │              ├─ ASSIGNED → assignDriver() → gửi notification thành công
 *      │              └─ FAILED   → refund() → cancel("Lỗi phân bổ giao vận") → notify hủy
 *      └─ FAILED  → cancel("Thanh toán thất bại") → notify lỗi
 * </pre>
 * </p>
 * <p>
 * Virtual Threads (Java 21) được kích hoạt toàn cục qua {@code spring.threads.virtual.enabled: true},
 * giúp các cuộc gọi mạng đồng bộ qua Feign không chặn luồng OS vật lý đắt đỏ.
 * </p>
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentServiceClient paymentClient;
    private final DeliveryServiceClient deliveryClient;
    private final NotificationServiceClient notificationClient;
    private final RestaurantServiceClient restaurantClient;
    private final TransactionTemplate transactionTemplate;
    private final BigDecimal deliveryFee;

    public OrderSagaOrchestrator(OrderRepository orderRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 PaymentServiceClient paymentClient,
                                 DeliveryServiceClient deliveryClient,
                                 NotificationServiceClient notificationClient,
                                 RestaurantServiceClient restaurantClient,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${app.order.pricing.delivery-fee:15000}") BigDecimal deliveryFee) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentClient = paymentClient;
        this.deliveryClient = deliveryClient;
        this.notificationClient = notificationClient;
        this.restaurantClient = restaurantClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        if (deliveryFee == null || deliveryFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Configured delivery fee cannot be negative");
        }
        this.deliveryFee = deliveryFee;
    }

    /**
     * Điều phối toàn bộ luồng đặt hàng theo Saga pattern.
     *
     * @param customerId      ID khách hàng
     * @param restaurantId    ID nhà hàng
     * @param deliveryAddress Địa chỉ giao hàng (JSON string)
     * @param requestedItems  Danh sách menu item và số lượng do client yêu cầu
     * @return Order sau khi hoàn tất saga (PAID hoặc CANCELLED)
     */
    public Order placeOrder(UUID customerId, UUID restaurantId,
                            String deliveryAddress,
                            List<RequestedItem> requestedItems) {
        return placeOrder(customerId, restaurantId, deliveryAddress,
                "legacy-" + UUID.randomUUID(), requestedItems);
    }

    public Order placeOrder(UUID customerId, UUID restaurantId,
                            String deliveryAddress, String clientRequestId,
                            List<RequestedItem> requestedItems) {

        validateClientRequestId(clientRequestId);
        var existingOrder = orderRepository.findByCustomerIdAndClientRequestId(customerId, clientRequestId);
        if (existingOrder.isPresent()) {
            Order existing = existingOrder.get();
            return existing.getStatus() == com.fooddelivery.order.domain.model.valueobject.OrderStatus.PENDING
                    ? reconcilePayment(existing.getId())
                    : existing;
        }

        log.info("🚀 Bắt đầu Saga đặt hàng: customerId={}, restaurantId={}", customerId, restaurantId);

        List<OrderItemInput> pricedItems = quoteAndValidateItems(restaurantId, requestedItems);

        // ── BƯỚC 1: Tạo Order ở trạng thái PENDING ──
        Order order = createPendingOrder(customerId, restaurantId, deliveryAddress,
                deliveryFee, BigDecimal.ZERO, clientRequestId, pricedItems);

        log.info("📝 Đơn hàng PENDING đã tạo: orderId={}, totalAmount={}",
                order.getId(), order.getTotalAmount());

        // ── BƯỚC 2: Gọi Payment Service ──
        var paymentRequest = new PaymentRequest(
                order.getId(), order.getCustomerId(), order.getTotalAmount());

        log.info("💳 Gọi Payment Service: orderId={}, amount={}", order.getId(), order.getTotalAmount());
        PaymentResponse paymentResponse;
        try {
            paymentResponse = paymentClient.processPayment(paymentIdempotencyKey(order), paymentRequest);
        } catch (Exception e) {
            log.warn("Không xác định được kết quả thanh toán cho order {}: {}", order.getId(), e.getMessage());
            return reconcilePayment(order.getId());
        }

        return handlePaymentResponse(order, paymentResponse);
    }

    public Order reconcilePayment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.fooddelivery.order.domain.exception.OrderNotFoundException(orderId));
        if (order.getStatus() != com.fooddelivery.order.domain.model.valueobject.OrderStatus.PENDING) {
            return order;
        }

        PaymentResponse paymentResponse;
        try {
            paymentResponse = paymentClient.getPaymentByOrderId(orderId);
        } catch (FeignException ex) {
            if (ex.status() != 404) {
                log.warn("Payment status lookup unavailable for order {}: {}", orderId, ex.getMessage());
                return order;
            }
            try {
                paymentResponse = paymentClient.processPayment(paymentIdempotencyKey(order),
                        new PaymentRequest(order.getId(), order.getCustomerId(), order.getTotalAmount()));
            } catch (RuntimeException retryFailure) {
                log.warn("Payment retry unavailable for order {}: {}", orderId, retryFailure.getMessage());
                return order;
            }
        } catch (RuntimeException ex) {
            log.warn("Payment reconciliation unavailable for order {}: {}", orderId, ex.getMessage());
            return order;
        }
        return handlePaymentResponse(order, paymentResponse);
    }

    private Order handlePaymentResponse(Order order, PaymentResponse paymentResponse) {
        if (paymentResponse == null
                || (paymentResponse.orderId() != null && !order.getId().equals(paymentResponse.orderId()))
                || !StringUtils.hasText(paymentResponse.status())) {
            log.warn("Payment service returned an invalid response for order {}", order.getId());
            return order;
        }
        return switch (paymentResponse.status().toUpperCase()) {
            case "SUCCESS" -> handlePaymentSuccess(order, paymentResponse);
            case "FAILED"  -> handlePaymentFailure(order, paymentResponse.message());
            case "REFUNDED" -> handlePaymentFailure(order, "Payment was already refunded");
            case "PENDING", "PROCESSING", "UNKNOWN" -> order;
            default -> {
                log.warn("⚠️ Trạng thái thanh toán không xác định: {}", paymentResponse.status());
                yield order;
            }
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE — Các bước trong Saga
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Bước 1: Tạo Order PENDING và lưu vào DB trong một transaction.
     */
    protected Order createPendingOrder(UUID customerId, UUID restaurantId,
                                       String deliveryAddress, BigDecimal deliveryFee,
                                       BigDecimal discountAmount,
                                       String clientRequestId,
                                       java.util.List<OrderItemInput> items) {
        return transactionTemplate.execute(status -> {
            Order order = Order.create(customerId, restaurantId, deliveryAddress,
                    deliveryFee, discountAmount, clientRequestId);

            // Thêm items vào order
            if (items != null) {
                for (var item : items) {
                    order.addItem(item.menuItemId(), item.itemName(), item.description(),
                            item.unitPrice(), item.quantity());
                }
            }

            // Persist outbox events first using original order object, then save order
            persistOutboxEvents(order);
            order = orderRepository.save(order);

            return order;
        });
    }

    /**
     * Thanh toán thành công → markAsPaid → gọi Delivery Service.
     */
    private Order handlePaymentSuccess(Order order, PaymentResponse paymentResponse) {
        log.info("✅ Thanh toán thành công: orderId={}, transactionId={}",
                order.getId(), paymentResponse.transactionId());

        // Cập nhật trạng thái PAID
        order = markOrderAsPaid(order);

        // ── BƯỚC 3: Gọi Delivery Service ──
        var deliveryRequest = new DeliveryRequest(
                order.getId(), order.getCustomerId(), order.getDeliveryAddressJson());

        log.info("🚚 Gọi Delivery Service: orderId={}", order.getId());
        DeliveryResponse deliveryResponse;
        try {
            deliveryResponse = deliveryClient.scheduleDelivery(deliveryRequest);
        } catch (Exception e) {
            log.error("❌ Lỗi kết nối Delivery Service: {}", e.getMessage());
            return handleDeliveryFailure(order, "Lỗi kết nối dịch vụ giao vận: " + e.getMessage());
        }

        // Xử lý kết quả giao vận bằng Switch Expression
        return switch (deliveryResponse.status().toUpperCase()) {
            case "ASSIGNED" -> handleDeliverySuccess(order, deliveryResponse);
            case "FAILED"   -> handleDeliveryFailure(order, deliveryResponse.message());
            default -> {
                log.warn("⚠️ Trạng thái giao vận không xác định: {}", deliveryResponse.status());
                yield handleDeliveryFailure(order, "Trạng thái giao vận không xác định");
            }
        };
    }

    /**
     * Thanh toán thất bại → cancel đơn hàng → gửi thông báo lỗi.
     */
    private Order handlePaymentFailure(Order order, String reason) {
        log.warn("❌ Thanh toán thất bại: orderId={}, reason={}", order.getId(), reason);

        order = cancelOrder(order, "Thanh toán thất bại");

        // Gửi thông báo lỗi thanh toán (fire-and-forget)
        sendNotificationSafe(order, "Đơn hàng bị hủy",
                "Đơn hàng " + order.getId() + " đã bị hủy do thanh toán thất bại: " + reason);

        return order;
    }

    /**
     * Giao vận thành công → assignDriver → gửi thông báo hoàn tất.
     */
    private Order handleDeliverySuccess(Order order, DeliveryResponse deliveryResponse) {
        log.info("✅ Phân bổ tài xế thành công: orderId={}, driverId={}",
                order.getId(), deliveryResponse.driverId());

        order = assignDriverToOrder(order, deliveryResponse.driverId());

        // Gửi thông báo hoàn tất
        sendNotificationSafe(order, "Đơn hàng đã được xác nhận",
                "Đơn hàng " + order.getId() + " đã được thanh toán và tài xế " +
                        deliveryResponse.driverId() + " đang trên đường giao hàng.");

        log.info("🎉 Saga hoàn tất thành công: orderId={}", order.getId());
        return order;
    }

    /**
     * Giao vận thất bại → COMPENSATING FLOW:
     *   1. Hoàn tiền qua Payment Service
     *   2. Hủy đơn hàng
     *   3. Gửi thông báo hủy
     */
    private Order handleDeliveryFailure(Order order, String reason) {
        log.warn("❌ Giao vận thất bại: orderId={}, reason={}", order.getId(), reason);

        // ── Compensating: Hoàn tiền ──
        try {
            var refundRequest = new RefundRequest(order.getId(), order.getTotalAmount());
            var refundResponse = paymentClient.refundPayment(refundRequest);
            log.info("💰 Hoàn tiền thành công: orderId={}, status={}", order.getId(), refundResponse.status());
        } catch (Exception e) {
            log.error("❌ Lỗi hoàn tiền: orderId={}, error={}", order.getId(), e.getMessage());
            // Ghi nhận lỗi hoàn tiền nhưng vẫn tiếp tục hủy đơn
        }

        // ── Compensating: Hủy đơn hàng ──
        order = cancelOrder(order, "Lỗi phân bổ giao vận");

        // ── Gửi thông báo hủy đơn và hoàn tiền ──
        sendNotificationSafe(order, "Đơn hàng bị hủy — Hoàn tiền",
                "Đơn hàng " + order.getId() + " đã bị hủy do: " + reason +
                        ". Số tiền " + order.getTotalAmount() + " VND đã được hoàn lại.");

        log.info("🔄 Saga compensating hoàn tất: orderId={}", order.getId());
        return order;
    }

    // ══════════════════════════════════════════════════════════════════════
    // TRANSACTIONAL HELPERS
    // ══════════════════════════════════════════════════════════════════════

    protected Order markOrderAsPaid(Order order) {
        return transactionTemplate.execute(status -> {
            Order existingOrder = orderRepository.findById(order.getId()).orElseThrow();
            existingOrder.markAsPaid();
            persistOutboxEvents(existingOrder);
            existingOrder = orderRepository.save(existingOrder);
            return existingOrder;
        });
    }

    protected Order assignDriverToOrder(Order order, UUID driverId) {
        return transactionTemplate.execute(status -> {
            Order existingOrder = orderRepository.findById(order.getId()).orElseThrow();
            existingOrder.assignDriver(driverId);
            persistOutboxEvents(existingOrder);
            existingOrder = orderRepository.save(existingOrder);
            return existingOrder;
        });
    }

    protected Order cancelOrder(Order order, String reason) {
        return transactionTemplate.execute(status -> {
            Order existingOrder = orderRepository.findById(order.getId()).orElseThrow();
            existingOrder.cancel(reason);
            persistOutboxEvents(existingOrder);
            existingOrder = orderRepository.save(existingOrder);
            return existingOrder;
        });
    }

    /**
     * Persist tất cả outbox event đang chờ từ aggregate root.
     */
    private void persistOutboxEvents(Order order) {
        if (!order.getPendingOutboxEvents().isEmpty()) {
            outboxEventRepository.saveAll(order.getPendingOutboxEvents());
            order.clearPendingOutboxEvents();
        }
    }

    /**
     * Gửi thông báo an toàn (fire-and-forget) — không để lỗi notification
     * ảnh hưởng đến luồng saga chính.
     */
    private void sendNotificationSafe(Order order, String subject, String message) {
        try {
            var request = new NotificationRequest(
                    order.getId(), order.getCustomerId(), "IN_APP", subject, message);
            notificationClient.sendNotification(request);
            log.info("📧 Thông báo đã gửi: orderId={}, subject={}", order.getId(), subject);
        } catch (Exception e) {
            log.warn("⚠️ Gửi thông báo thất bại (non-critical): orderId={}, error={}",
                    order.getId(), e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // INPUT DTOs
    // ══════════════════════════════════════════════════════════════════════

    public record RequestedItem(UUID menuItemId, int quantity) {}

    /**
     * Server-authoritative item snapshot returned by restaurant-service.
     */
    public record OrderItemInput(
            UUID menuItemId,
            String itemName,
            String description,
            BigDecimal unitPrice,
            int quantity
    ) {}

    private List<OrderItemInput> quoteAndValidateItems(UUID restaurantId, List<RequestedItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new InvalidOrderRequestException("At least one menu item is required");
        }

        MenuQuoteRequest request = new MenuQuoteRequest(requestedItems.stream()
                .map(item -> new MenuQuoteRequest.Item(item.menuItemId(), item.quantity()))
                .toList());
        MenuQuoteResponse quote;
        try {
            quote = restaurantClient.quoteMenu(restaurantId, request);
        } catch (FeignException ex) {
            if (ex.status() >= 400 && ex.status() < 500) {
                throw new InvalidOrderRequestException("Restaurant rejected the requested menu items", ex);
            }
            throw new OrderDependencyException("Restaurant pricing service is unavailable", ex);
        } catch (RuntimeException ex) {
            throw new OrderDependencyException("Restaurant pricing service is unavailable", ex);
        }

        if (quote == null || !restaurantId.equals(quote.restaurantId()) || quote.items() == null) {
            throw new OrderDependencyException("Restaurant pricing service returned an invalid quote", null);
        }

        Map<UUID, Integer> expectedQuantities = aggregateRequestedQuantities(requestedItems);
        Map<UUID, Integer> quotedQuantities = new LinkedHashMap<>();
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;
        var pricedItems = new java.util.ArrayList<OrderItemInput>();

        for (MenuQuoteResponse.Item item : quote.items()) {
            if (item == null || item.menuItemId() == null || item.itemName() == null || item.itemName().isBlank()
                    || item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) <= 0
                    || item.quantity() <= 0) {
                throw new OrderDependencyException("Restaurant pricing service returned an invalid item", null);
            }
            if (quotedQuantities.putIfAbsent(item.menuItemId(), item.quantity()) != null) {
                throw new OrderDependencyException("Restaurant pricing service returned duplicate items", null);
            }
            BigDecimal lineTotal = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            if (item.lineTotal() == null || lineTotal.compareTo(item.lineTotal()) != 0) {
                throw new OrderDependencyException("Restaurant pricing service returned an invalid line total", null);
            }
            calculatedSubtotal = calculatedSubtotal.add(lineTotal);
            pricedItems.add(new OrderItemInput(
                    item.menuItemId(), item.itemName(), item.description(), item.unitPrice(), item.quantity()));
        }

        if (!expectedQuantities.equals(quotedQuantities)
                || quote.subtotal() == null
                || calculatedSubtotal.compareTo(quote.subtotal()) != 0) {
            throw new OrderDependencyException("Restaurant pricing service returned an inconsistent quote", null);
        }
        return List.copyOf(pricedItems);
    }

    private Map<UUID, Integer> aggregateRequestedQuantities(List<RequestedItem> requestedItems) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (RequestedItem item : requestedItems) {
            if (item == null || item.menuItemId() == null || item.quantity() <= 0 || item.quantity() > 99) {
                throw new InvalidOrderRequestException("Invalid menu item or quantity");
            }
            int quantity;
            try {
                quantity = Math.addExact(quantities.getOrDefault(item.menuItemId(), 0), item.quantity());
            } catch (ArithmeticException ex) {
                throw new InvalidOrderRequestException("Invalid combined item quantity", ex);
            }
            if (quantity > 99) {
                throw new InvalidOrderRequestException("Combined quantity cannot exceed 99 per menu item");
            }
            quantities.put(item.menuItemId(), quantity);
        }
        return quantities;
    }

    private String paymentIdempotencyKey(Order order) {
        return "order-payment:" + order.getId();
    }

    private void validateClientRequestId(String clientRequestId) {
        if (!StringUtils.hasText(clientRequestId) || clientRequestId.length() > 100) {
            throw new InvalidOrderRequestException("A valid Idempotency-Key header is required");
        }
    }
}
