package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.CancelOrderRequest;
import com.fooddelivery.order.api.dto.CreateOrderRequest;
import com.fooddelivery.order.api.dto.OrderEtaResponse;
import com.fooddelivery.order.api.dto.OrderPreviewResponse;
import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.application.OrderCancellationService;
import com.fooddelivery.order.domain.exception.InvalidOrderRequestException;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OrderItem;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderSagaOrchestrator;
import com.fooddelivery.order.security.OrderAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private static final Duration FIXED_ETA = Duration.ofHours(1);
    /** Default history = terminal outcomes (delivered / cancelled). */
    private static final Set<OrderStatus> DEFAULT_HISTORY_STATUSES =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderSagaOrchestrator sagaOrchestrator;
    private final OrderAuthorizationService orderAuthorization;
    private final OrderCancellationService orderCancellationService;
    private final Clock clock;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        UUID customerId = UUID.fromString(authentication.getName());
        log.info("Create order: customerId={}, restaurantId={}", customerId, request.restaurantId());

        var requestedItems = request.items().stream()
                .map(item -> new OrderSagaOrchestrator.RequestedItem(item.menuItemId(), item.quantity()))
                .toList();

        Order order = sagaOrchestrator.placeOrder(
                customerId,
                request.restaurantId(),
                request.deliveryAddress(),
                idempotencyKey,
                requestedItems,
                request.note()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toResponse(order));
    }

    /**
     * Preview pricing without creating an order (menu quote + configured delivery fee).
     */
    @PostMapping("/preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderPreviewResponse> preview(
            @Valid @RequestBody CreateOrderRequest request) {
        var requestedItems = request.items().stream()
                .map(item -> new OrderSagaOrchestrator.RequestedItem(item.menuItemId(), item.quantity()))
                .toList();
        var quote = sagaOrchestrator.preview(request.restaurantId(), requestedItems);
        var lines = quote.items().stream()
                .map(i -> new OrderPreviewResponse.Line(
                        i.menuItemId(), i.name(), i.unitPrice(), i.quantity(), i.lineTotal()))
                .toList();
        return ResponseEntity.ok(new OrderPreviewResponse(
                quote.restaurantId(),
                quote.subtotal(),
                quote.deliveryFee(),
                quote.discountAmount(),
                quote.totalAmount(),
                lines));
    }

    /**
     * List/search orders.
     * Customer: own orders. Admin: system-wide filters (status, restaurantId, from, to, userId/customerId).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        UUID filterUser = userId != null ? userId : customerId;
        UUID customerFilter = orderAuthorization.resolveListCustomerFilter(filterUser, authentication);
        if (restaurantId != null && !orderAuthorization.hasRole(authentication, "ADMIN")) {
            // non-admin may not filter other restaurants via this endpoint
            restaurantId = null;
        }
        Page<Order> orders = orderRepository.findAllFiltered(
                customerFilter, status, restaurantId, from, to, pageable);
        return ResponseEntity.ok(orders.map(orderMapper::toResponse));
    }

    /**
     * Customer order history (alias path for FE).
     * Default statuses: DELIVERED, CANCELLED. Pass {@code status} to narrow to one.
     */
    @GetMapping("/history/{customerId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Page<OrderResponse>> history(
            @PathVariable UUID customerId,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        UUID resolved = orderAuthorization.resolveHistoryCustomerId(customerId, authentication);
        Set<OrderStatus> statuses = status != null ? EnumSet.of(status) : DEFAULT_HISTORY_STATUSES;
        Page<Order> orders = orderRepository.findHistoryByCustomerAndStatusIn(resolved, statuses, pageable);
        return ResponseEntity.ok(orders.map(orderMapper::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@orderAuthorization.canRead(#id, authentication)")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        Order order = orderRepository.findDetailedById(id)
                .or(() -> orderRepository.findById(id))
                .orElseThrow(() -> new OrderNotFoundException(id));
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    /**
     * Fixed ETA stub: always 60 minutes from now (product placeholder).
     */
    @GetMapping("/{id}/eta")
    @PreAuthorize("@orderAuthorization.canRead(#id, authentication)")
    public ResponseEntity<OrderEtaResponse> getEta(@PathVariable UUID id) {
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException(id);
        }
        Instant now = clock.instant();
        return ResponseEntity.ok(new OrderEtaResponse(
                id,
                (int) FIXED_ETA.toMinutes(),
                now.plus(FIXED_ETA),
                "Fixed ETA stub (1 hour)"));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@orderAuthorization.canCancel(#id, authentication)")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request,
            Authentication authentication) {
        boolean admin = orderAuthorization.hasRole(authentication, "ADMIN");
        Order order = orderCancellationService.cancel(id, request.reason(), admin);
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    /**
     * Reorder: clone items + restaurant + delivery address from a past order,
     * re-quote current menu prices, and place a new order (same saga/payment path).
     */
    @PostMapping("/{id}/reorder")
    @PreAuthorize("@orderAuthorization.canReorder(#id, authentication)")
    public ResponseEntity<OrderResponse> reorder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        UUID customerId = UUID.fromString(authentication.getName());
        Order source = orderRepository.findDetailedById(id)
                .or(() -> orderRepository.findById(id))
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (!customerId.equals(source.getCustomerId())) {
            throw new OrderNotFoundException(id);
        }
        List<OrderItem> items = source.getItems();
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderRequestException("Source order has no items to reorder");
        }

        String address = source.getDeliveryAddressJson();
        if (!StringUtils.hasText(address)) {
            throw new InvalidOrderRequestException("Source order has no delivery address");
        }

        var requestedItems = items.stream()
                .map(i -> new OrderSagaOrchestrator.RequestedItem(i.getMenuItemId(), i.getQuantity()))
                .toList();

        String clientRequestId = StringUtils.hasText(idempotencyKey)
                ? idempotencyKey
                : "reorder-" + id + "-" + UUID.randomUUID();

        log.info("Reorder: sourceOrderId={}, customerId={}, restaurantId={}",
                id, customerId, source.getRestaurantId());

        Order created = sagaOrchestrator.placeOrder(
                customerId,
                source.getRestaurantId(),
                address,
                clientRequestId,
                requestedItems,
                source.getNote()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toResponse(created));
    }
}
