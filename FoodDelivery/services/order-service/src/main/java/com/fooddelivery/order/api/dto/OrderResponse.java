package com.fooddelivery.order.api.dto;

import com.fooddelivery.order.domain.model.valueobject.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for the {@code GET /api/v1/orders/{id}} response.
 */
public record OrderResponse(
        UUID id,
        UUID customerId,
        UUID restaurantId,
        OrderStatus status,
        BigDecimal totalAmount,
        AssignedDriverDto assignedDriver,
        Instant createdAt,
        Instant updatedAt
) {}
