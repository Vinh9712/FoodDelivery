package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.CreateOrderRequest;
import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.infrastructure.messaging.OrderPlacedEventPublisher;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for Order resources.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderPlacedEventPublisher eventPublisher;

    /**
     * Create a new order and publish order.placed event to Kafka.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@jakarta.validation.Valid @RequestBody CreateOrderRequest request) {
        Order order = new Order(request.customerId(), request.restaurantId(), request.totalAmount());
        order = orderRepository.save(order);

        // Publish event — delivery-service, payment-service, notification-service will consume it
        eventPublisher.publish(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getTotalAmount(),
                request.currency()
        );

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(orderMapper.toResponse(order));
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

