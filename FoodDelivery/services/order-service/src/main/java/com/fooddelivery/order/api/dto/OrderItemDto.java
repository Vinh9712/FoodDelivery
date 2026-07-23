package com.fooddelivery.order.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDto(
        UUID id,
        UUID menuItemId,
        String name,
        String description,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
