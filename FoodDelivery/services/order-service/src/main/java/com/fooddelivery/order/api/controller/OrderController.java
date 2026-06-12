package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
