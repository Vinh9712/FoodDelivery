package com.fooddelivery.order.api.dto;

import com.fooddelivery.order.domain.model.valueobject.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusHistoryDto(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String note,
        UUID changedBy,
        Instant createdAt
) {
}
