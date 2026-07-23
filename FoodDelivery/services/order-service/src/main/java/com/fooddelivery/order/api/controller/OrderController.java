package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.CreateOrderRequest;
import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.dto.OrderHistoryResponse;
import com.fooddelivery.order.api.dto.EtaResponse;
import com.fooddelivery.order.api.dto.CancelRequest;
import com.fooddelivery.order.api.dto.ReorderRequest;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderSagaOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderSagaOrchestrator sagaOrchestrator;

    // ==================== EXISTING METHODS ====================

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        UUID customerId = UUID.fromString(authentication.getName());
        log.info("📥 Received create order request: customerId={}, restaurantId={}",
                customerId, request.restaurantId());

        var requestedItems = request.items().stream()
                .map(item -> new OrderSagaOrchestrator.RequestedItem(item.menuItemId(), item.quantity()))
                .toList();

        Order order = sagaOrchestrator.placeOrder(
                customerId,
                request.restaurantId(),
                request.deliveryAddress(),
                idempotencyKey,
                requestedItems
        );

        log.info("✅ Saga completed: orderId={}, status={}", order.getId(), order.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toResponse(order));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@orderAuthorization.canRead(#id, authentication)")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    // ==================== NEW METHODS ====================

    /**
     * 1. List orders with filter by status (Pagination)
     */
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @RequestParam UUID customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        UUID authenticatedCustomerId = UUID.fromString(authentication.getName());
        if (!authenticatedCustomerId.equals(customerId)) {
            log.warn("⚠️ User {} tried to access orders of {}", authenticatedCustomerId, customerId);
            throw new SecurityException("You can only access your own orders");
        }

        log.info("📋 Getting orders: customerId={}, status={}, page={}, size={}",
                customerId, status, page, size);

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Order> orders;

        if (status != null) {
            orders = orderRepository.findByCustomerIdAndStatus(customerId, status, pageRequest);
        } else {
            orders = orderRepository.findByCustomerId(customerId, pageRequest);
        }

        return ResponseEntity.ok(orders.map(orderMapper::toResponse));
    }

    /**
     * 2. Order history
     */
    @GetMapping("/history/{customerId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<OrderHistoryResponse>> getOrderHistory(
            @PathVariable UUID customerId,
            @RequestParam(required = false) OrderStatus status,
            Authentication authentication) {

        UUID authenticatedCustomerId = UUID.fromString(authentication.getName());
        if (!authenticatedCustomerId.equals(customerId)) {
            log.warn("⚠️ User {} tried to access history of {}", authenticatedCustomerId, customerId);
            throw new SecurityException("You can only access your own order history");
        }

        log.info("📜 Getting order history: customerId={}, status={}", customerId, status);

        List<Order> orders;
        if (status != null) {
            orders = orderRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, status);
        } else {
            orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        }

        return ResponseEntity.ok(orders.stream()
                .map(orderMapper::toHistoryResponse)
                .collect(Collectors.toList()));
    }

    /**
     * 3. Reorder (buy again from previous order)
     */
    @PostMapping("/{orderId}/reorder")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> reorder(
            @PathVariable UUID orderId,
            @Valid @RequestBody ReorderRequest request,
            Authentication authentication) {

        UUID customerId = UUID.fromString(authentication.getName());
        log.info("🔄 Reorder: customerId={}, from orderId={}", customerId, orderId);

        Order existingOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!existingOrder.getCustomerId().equals(customerId)) {
            log.warn("⚠️ User {} tried to reorder from order {} of another customer",
                    customerId, orderId);
            throw new SecurityException("You can only reorder from your own orders");
        }

        // Create new order from existing using the factory method
        Order newOrder = Order.create(
                existingOrder.getCustomerId(),
                existingOrder.getRestaurantId(),
                request.getDeliveryAddress() != null ?
                        request.getDeliveryAddress() : existingOrder.getDeliveryAddressJson(),
                existingOrder.getDeliveryFee(),
                existingOrder.getDiscountAmount(),
                null // clientRequestId
        );

        // Copy items from existing order
        existingOrder.getItems().forEach(item -> {
            newOrder.addItem(
                    item.getMenuItemId(),
                    item.getItemName(),
                    item.getItemDescription(),
                    item.getUnitPrice(),
                    item.getQuantity()
            );
        });

        Order savedOrder = orderRepository.save(newOrder);
        log.info("✅ Reorder successful: newOrderId={}", savedOrder.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toResponse(savedOrder));
    }

    /**
     * 4. Cancel order
     */
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelRequest request,
            Authentication authentication) {

        UUID customerId = UUID.fromString(authentication.getName());
        log.info("❌ Cancelling order: customerId={}, orderId={}, reason={}",
                customerId, orderId, request.getReason());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getCustomerId().equals(customerId)) {
            log.warn("⚠️ User {} tried to cancel order {} of another customer",
                    customerId, orderId);
            throw new SecurityException("You can only cancel your own orders");
        }

        // Use existing cancel method in Order entity
        order.cancel(request.getReason(), customerId);

        Order cancelledOrder = orderRepository.save(order);
        log.info("✅ Order cancelled: orderId={}", orderId);

        // TODO: Call Payment Service for refund if already paid

        return ResponseEntity.ok(orderMapper.toResponse(cancelledOrder));
    }

    /**
     * 5. Calculate ETA (Estimated Time of Arrival)
     */
    @GetMapping("/{orderId}/eta")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<EtaResponse> getEstimatedDeliveryTime(
            @PathVariable UUID orderId,
            Authentication authentication) {

        UUID customerId = UUID.fromString(authentication.getName());
        log.info("⏱️ Calculating ETA: customerId={}, orderId={}", customerId, orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getCustomerId().equals(customerId)) {
            log.warn("⚠️ User {} tried to get ETA of order {}", customerId, orderId);
            throw new SecurityException("You can only get ETA of your own orders");
        }

        // Calculate estimated delivery time
        int estimatedMinutes = calculateEstimatedMinutes();

        EtaResponse response = EtaResponse.builder()
                .orderId(orderId)
                .estimatedMinutes(estimatedMinutes)
                .status(order.getStatus())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to calculate ETA
     * TODO: Implement actual calculation with external service
     */
    private int calculateEstimatedMinutes() {
        // Simple default calculation
        // In real implementation, call Delivery Service or Google Maps API
        return 30 + (int)(Math.random() * 15); // 30-45 minutes
    }
}