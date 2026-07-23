package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.CancelOrderRequest;
import com.fooddelivery.order.api.dto.CreateOrderRequest;
import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.application.OrderCancellationService;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Order resources.
 * <p>
 * Endpoint {@code POST /api/v1/orders} kích hoạt luồng Saga điều phối
 * thanh toán → giao vận → thông báo.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderSagaOrchestrator sagaOrchestrator;
    private final OrderAuthorizationService orderAuthorization;
    private final OrderCancellationService orderCancellationService;

    /**
     * Tạo đơn hàng mới và thực thi Saga đặt hàng.
     *
     * @param request chỉ gồm restaurant, địa chỉ và menu item ID/số lượng; danh tính và giá do server xác định
     * @return OrderResponse với trạng thái cuối cùng sau khi saga hoàn tất
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        UUID customerId = UUID.fromString(authentication.getName());
        log.info("📥 Nhận yêu cầu tạo đơn hàng: customerId={}, restaurantId={}",
                customerId, request.restaurantId());

        var requestedItems = request.items().stream()
                .map(item -> new OrderSagaOrchestrator.RequestedItem(item.menuItemId(), item.quantity()))
                .toList();

        // Kích hoạt Saga
        Order order = sagaOrchestrator.placeOrder(
                customerId,
                request.restaurantId(),
                request.deliveryAddress(),
                idempotencyKey,
                requestedItems
        );

        log.info("✅ Saga hoàn tất: orderId={}, status={}", order.getId(), order.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toResponse(order));
    }

    /**
     * List orders with optional filters.
     * <ul>
     *   <li>ADMIN: all orders; optional {@code userId} and {@code status}</li>
     *   <li>CUSTOMER: own orders only; optional {@code userId} (must match principal) and {@code status}</li>
     * </ul>
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        UUID customerFilter = orderAuthorization.resolveListCustomerFilter(userId, authentication);
        Page<Order> orders = orderRepository.findAllFiltered(customerFilter, status, pageable);
        return ResponseEntity.ok(orders.map(orderMapper::toResponse));
    }

    /**
     * Get order details by ID, including items, address, history, and assigned driver.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@orderAuthorization.canRead(#id, authentication)")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        Order order = orderRepository.findDetailedById(id)
                .or(() -> orderRepository.findById(id))
                .orElseThrow(() -> new OrderNotFoundException(id));
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    /**
     * Cancel order (customer owner or admin).
     * <ul>
     *   <li>PENDING → CANCELLED (no refund)</li>
     *   <li>PAID/CONFIRMED (customer) or broader statuses (admin) → compensation + refund path</li>
     * </ul>
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("@orderAuthorization.canCancel(#id, authentication)")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request,
            Authentication authentication) {
        boolean admin = orderAuthorization.hasRole(authentication, "ADMIN");
        log.info("Cancel order request: orderId={}, admin={}", id, admin);
        Order order = orderCancellationService.cancel(id, request.reason(), admin);
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

}
