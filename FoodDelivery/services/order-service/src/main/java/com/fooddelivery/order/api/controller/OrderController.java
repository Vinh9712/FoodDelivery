package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.CreateOrderRequest;
import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    /**
     * Tạo đơn hàng mới và thực thi Saga đặt hàng.
     *
     * @param request chứa thông tin đơn hàng (customerId, restaurantId, items, address, ...)
     * @return OrderResponse với trạng thái cuối cùng sau khi saga hoàn tất
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        log.info("📥 Nhận yêu cầu tạo đơn hàng: customerId={}, restaurantId={}",
                request.customerId(), request.restaurantId());

        // Map input items sang format mà Saga Orchestrator cần
        var sagaItems = request.items() != null
                ? request.items().stream()
                    .map(item -> new OrderSagaOrchestrator.OrderItemInput(
                            item.menuItemId(), item.itemName(), item.description(),
                            item.unitPrice(), item.quantity()))
                    .toList()
                : java.util.List.<OrderSagaOrchestrator.OrderItemInput>of();

        // Kích hoạt Saga
        Order order = sagaOrchestrator.placeOrder(
                request.customerId(),
                request.restaurantId(),
                request.deliveryAddress(),
                request.deliveryFee(),
                request.discountAmount(),
                sagaItems
        );

        log.info("✅ Saga hoàn tất: orderId={}, status={}", order.getId(), order.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toResponse(order));
    }

    /**
     * Get order details by ID, including assigned driver snapshot if available.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }
}
