package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.dto.RejectOrderRequest;
import com.fooddelivery.order.api.dto.RestaurantOrderStatsResponse;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.application.RestaurantOrderService;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.security.RestaurantOrderAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurant-orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
public class RestaurantOrderController {

    private final RestaurantOrderService restaurantOrderService;
    private final RestaurantOrderAuthorizationService authorizationService;
    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> list(
            @RequestParam UUID restaurantId,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        authorizationService.assertCanManageRestaurant(restaurantId, authentication);
        Page<Order> orders = restaurantOrderService.list(restaurantId, status, pageable);
        return ResponseEntity.ok(orders.map(orderMapper::toResponse));
    }

    @PostMapping("/{orderId}/accept")
    public ResponseEntity<OrderResponse> accept(
            @PathVariable UUID orderId,
            Authentication authentication) {
        authorizationService.assertCanManageOrder(orderId, authentication);
        UUID actorId = UUID.fromString(authentication.getName());
        Order order = restaurantOrderService.accept(orderId, actorId);
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    @PostMapping("/{orderId}/start-preparing")
    public ResponseEntity<OrderResponse> startPreparing(
            @PathVariable UUID orderId,
            Authentication authentication) {
        authorizationService.assertCanManageOrder(orderId, authentication);
        UUID actorId = UUID.fromString(authentication.getName());
        Order order = restaurantOrderService.startPreparing(orderId, actorId);
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    @PostMapping("/{orderId}/ready")
    public ResponseEntity<OrderResponse> markReady(
            @PathVariable UUID orderId,
            Authentication authentication) {
        authorizationService.assertCanManageOrder(orderId, authentication);
        UUID actorId = UUID.fromString(authentication.getName());
        Order order = restaurantOrderService.markReady(orderId, actorId);
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<OrderResponse> reject(
            @PathVariable UUID orderId,
            @Valid @RequestBody RejectOrderRequest request,
            Authentication authentication) {
        authorizationService.assertCanManageOrder(orderId, authentication);
        UUID actorId = UUID.fromString(authentication.getName());
        Order order = restaurantOrderService.reject(orderId, actorId, request.reason());
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    /**
     * Cancel after accept (CONFIRMED/PREPARING) — same compensation path as reject.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable UUID orderId,
            @Valid @RequestBody RejectOrderRequest request,
            Authentication authentication) {
        return reject(orderId, request, authentication);
    }

    @GetMapping("/stats")
    public ResponseEntity<RestaurantOrderStatsResponse> stats(
            @RequestParam UUID restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            Authentication authentication) {
        authorizationService.assertCanManageRestaurant(restaurantId, authentication);
        Instant from = since != null ? since : Instant.now().minus(30, ChronoUnit.DAYS);
        long total = orderRepository.countByRestaurantSince(restaurantId, from);
        long delivered = orderRepository.countByRestaurantAndStatusSince(
                restaurantId, OrderStatus.DELIVERED, from);
        long cancelled = orderRepository.countByRestaurantAndStatusSince(
                restaurantId, OrderStatus.CANCELLED, from)
                + orderRepository.countByRestaurantAndStatusSince(
                restaurantId, OrderStatus.CANCELLATION_PENDING, from);
        long active = Math.max(0, total - delivered - cancelled);
        BigDecimal revenue = orderRepository.sumDeliveredRevenueSince(restaurantId, from);
        return ResponseEntity.ok(new RestaurantOrderStatsResponse(
                restaurantId, from, total, delivered, cancelled, active,
                revenue != null ? revenue : BigDecimal.ZERO));
    }
}
