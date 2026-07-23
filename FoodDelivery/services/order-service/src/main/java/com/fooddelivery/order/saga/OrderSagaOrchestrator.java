package com.fooddelivery.order.saga;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.exception.InvalidOrderRequestException;
import com.fooddelivery.order.domain.exception.OrderDependencyException;
import com.fooddelivery.order.domain.model.valueobject.PickupAddressSnapshot;
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
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentServiceClient paymentClient;
    private final NotificationServiceClient notificationClient;
    private final RestaurantServiceClient restaurantClient;
    private final TransactionTemplate transactionTemplate;
    private final BigDecimal deliveryFee;
    private final Clock clock;
    private final Duration restaurantAcceptanceTimeout;

    public OrderSagaOrchestrator(OrderRepository orderRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 PaymentServiceClient paymentClient,
                                 NotificationServiceClient notificationClient,
                                 RestaurantServiceClient restaurantClient,
                                 PlatformTransactionManager transactionManager,
                                 Clock clock,
                                 @Value("${app.order.pricing.delivery-fee:15000}") BigDecimal deliveryFee,
                                 @Value("${order.restaurant-acceptance-timeout:10m}") Duration restaurantAcceptanceTimeout) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentClient = paymentClient;
        this.notificationClient = notificationClient;
        this.restaurantClient = restaurantClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        if (deliveryFee == null || deliveryFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Configured delivery fee cannot be negative");
        }
        this.deliveryFee = deliveryFee;
        if (restaurantAcceptanceTimeout == null || restaurantAcceptanceTimeout.isNegative()
                || restaurantAcceptanceTimeout.isZero()) {
            throw new IllegalArgumentException("restaurant-acceptance-timeout must be positive");
        }
        this.restaurantAcceptanceTimeout = restaurantAcceptanceTimeout;
    }


    public Order placeOrder(UUID customerId, UUID restaurantId,
                            String deliveryAddress,
                            List<RequestedItem> requestedItems) {
        return placeOrder(customerId, restaurantId, deliveryAddress,
                "legacy-" + UUID.randomUUID(), requestedItems, null);
    }

    public Order placeOrder(UUID customerId, UUID restaurantId,
                            String deliveryAddress, String clientRequestId,
                            List<RequestedItem> requestedItems) {
        return placeOrder(customerId, restaurantId, deliveryAddress, clientRequestId, requestedItems, null);
    }

    public Order placeOrder(UUID customerId, UUID restaurantId,
                            String deliveryAddress, String clientRequestId,
                            List<RequestedItem> requestedItems,
                            String note) {

        validateClientRequestId(clientRequestId);
        var existingOrder = orderRepository.findByCustomerIdAndClientRequestId(customerId, clientRequestId);
        if (existingOrder.isPresent()) {
            Order existing = existingOrder.get();
            return existing.getStatus() == com.fooddelivery.order.domain.model.valueobject.OrderStatus.PENDING
                    ? reconcilePayment(existing.getId())
                    : existing;
        }

        log.info(" Bắt đầu Saga đặt hàng: customerId={}, restaurantId={}", customerId, restaurantId);

        PricedQuote pricedQuote = quoteAndValidateItems(restaurantId, requestedItems);

        // ── BƯỚC 1: Tạo Order ở trạng thái PENDING (kèm immutable pickup snapshot) ──
        Order order = createPendingOrder(customerId, restaurantId, deliveryAddress,
                deliveryFee, BigDecimal.ZERO, clientRequestId, pricedQuote, note);

        log.info(" Đơn hàng PENDING đã tạo: orderId={}, totalAmount={}",
                order.getId(), order.getTotalAmount());

        // ── BƯỚC 2: Gọi Payment Service ──
        var paymentRequest = new PaymentRequest(
                order.getId(), order.getCustomerId(), order.getTotalAmount());

        log.info(" Gọi Payment Service: orderId={}, amount={}", order.getId(), order.getTotalAmount());
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
                log.warn("️ Trạng thái thanh toán không xác định: {}", paymentResponse.status());
                yield order;
            }
        };
    }


    protected Order createPendingOrder(UUID customerId, UUID restaurantId,
                                       String deliveryAddress, BigDecimal deliveryFee,
                                       BigDecimal discountAmount,
                                       String clientRequestId,
                                       PricedQuote pricedQuote) {
        return createPendingOrder(customerId, restaurantId, deliveryAddress, deliveryFee,
                discountAmount, clientRequestId, pricedQuote, null);
    }

    protected Order createPendingOrder(UUID customerId, UUID restaurantId,
                                       String deliveryAddress, BigDecimal deliveryFee,
                                       BigDecimal discountAmount,
                                       String clientRequestId,
                                       PricedQuote pricedQuote,
                                       String note) {
        return transactionTemplate.execute(status -> {
            Order order = Order.create(customerId, restaurantId, deliveryAddress,
                    deliveryFee, discountAmount, clientRequestId, pricedQuote.pickup());

            if (pricedQuote.items() != null) {
                for (var item : pricedQuote.items()) {
                    order.addItem(item.menuItemId(), item.itemName(), item.description(),
                            item.unitPrice(), item.quantity());
                }
            }
            order.applyNote(note);

            persistOutboxEvents(order);
            order = orderRepository.save(order);

            return order;
        });
    }

    /**
     * Server-side price preview without creating an order (reuse menu quote + delivery fee).
     */
    public OrderPreviewQuote preview(UUID restaurantId, List<RequestedItem> requestedItems) {
        PricedQuote pricedQuote = quoteAndValidateItems(restaurantId, requestedItems);
        BigDecimal subtotal = BigDecimal.ZERO;
        var lines = new java.util.ArrayList<OrderPreviewQuote.Line>();
        if (pricedQuote.items() != null) {
            for (var item : pricedQuote.items()) {
                BigDecimal line = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
                subtotal = subtotal.add(line);
                lines.add(new OrderPreviewQuote.Line(
                        item.menuItemId(), item.itemName(), item.unitPrice(), item.quantity(), line));
            }
        }
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(deliveryFee).subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        return new OrderPreviewQuote(restaurantId, subtotal, deliveryFee, discount, total, List.copyOf(lines));
    }

    public record OrderPreviewQuote(
            UUID restaurantId,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            List<Line> items) {
        public record Line(
                UUID menuItemId,
                String name,
                BigDecimal unitPrice,
                int quantity,
                BigDecimal lineTotal) {
        }
    }

    /**
     * Payment success stops at PAID. Delivery is scheduled only after READY_FOR_PICKUP (Task 7).
     */
    private Order handlePaymentSuccess(Order order, PaymentResponse paymentResponse) {
        log.info(" Thanh toán thành công: orderId={}, transactionId={}",
                order.getId(), paymentResponse.transactionId());
        return markOrderAsPaid(order);
    }

    /**
     * Explicit payment failure cancels without refund (no captured payment).
     */
    private Order handlePaymentFailure(Order order, String reason) {
        log.warn(" Thanh toán thất bại: orderId={}, reason={}", order.getId(), reason);

        String message = StringUtils.hasText(reason) ? reason.trim() : "Payment failed";
        order = markOrderPaymentFailed(order, message);

        sendNotificationSafe(order, "Đơn hàng bị hủy",
                "Đơn hàng " + order.getId() + " đã bị hủy do thanh toán thất bại: " + message);

        return order;
    }

    // ══════════════════════════════════════════════════════════════════════
    // TRANSACTIONAL HELPERS
    // ══════════════════════════════════════════════════════════════════════

    protected Order markOrderAsPaid(Order order) {
        return transactionTemplate.execute(status -> {
            Order existingOrder = orderRepository.findById(order.getId()).orElseThrow();
            existingOrder.markPaid(clock.instant(), restaurantAcceptanceTimeout);
            persistOutboxEvents(existingOrder);
            existingOrder = orderRepository.save(existingOrder);
            return existingOrder;
        });
    }

    protected Order markOrderPaymentFailed(Order order, String reason) {
        return transactionTemplate.execute(status -> {
            Order existingOrder = orderRepository.findById(order.getId()).orElseThrow();
            existingOrder.markPaymentFailed(reason, clock.instant());
            persistOutboxEvents(existingOrder);
            existingOrder = orderRepository.save(existingOrder);
            return existingOrder;
        });
    }

    private void persistOutboxEvents(Order order) {
        if (!order.getPendingOutboxEvents().isEmpty()) {
            outboxEventRepository.saveAll(order.getPendingOutboxEvents());
            order.clearPendingOutboxEvents();
        }
    }

    private void sendNotificationSafe(Order order, String subject, String message) {
        try {
            var request = new NotificationRequest(
                    order.getId(), order.getCustomerId(), "IN_APP", subject, message);
            notificationClient.sendNotification(request);
            log.info(" Thông báo đã gửi: orderId={}, subject={}", order.getId(), subject);
        } catch (Exception e) {
            log.warn(" Gửi thông báo thất bại (non-critical): orderId={}, error={}",
                    order.getId(), e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // INPUT DTOs
    // ══════════════════════════════════════════════════════════════════════

    public record RequestedItem(UUID menuItemId, int quantity) {}

    public record OrderItemInput(
            UUID menuItemId,
            String itemName,
            String description,
            BigDecimal unitPrice,
            int quantity
    ) {}

    private record PricedQuote(List<OrderItemInput> items, PickupAddressSnapshot pickup) {}

    private PricedQuote quoteAndValidateItems(UUID restaurantId, List<RequestedItem> requestedItems) {
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

        PickupAddressSnapshot pickup = mapPickupSnapshot(restaurantId, quote.pickup());

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
        return new PricedQuote(List.copyOf(pricedItems), pickup);
    }

    private PickupAddressSnapshot mapPickupSnapshot(UUID restaurantId, MenuQuoteResponse.PickupSnapshot pickup) {
        if (pickup == null) {
            throw new OrderDependencyException("Restaurant pricing service returned a quote without pickup snapshot", null);
        }
        if (!restaurantId.equals(pickup.restaurantId())) {
            throw new OrderDependencyException("Restaurant pricing service returned a pickup snapshot for a different restaurant", null);
        }
        try {
            return new PickupAddressSnapshot(
                    pickup.restaurantId(),
                    pickup.name(),
                    pickup.phone(),
                    pickup.addressText(),
                    pickup.latitude(),
                    pickup.longitude());
        } catch (RuntimeException ex) {
            throw new OrderDependencyException("Restaurant pricing service returned an invalid pickup snapshot", ex);
        }
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
