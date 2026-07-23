package com.fooddelivery.restaurant.api.dto.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MenuQuoteResponse(
        UUID restaurantId,
        BigDecimal subtotal,
        List<Item> items
) {
    public record Item(
            UUID menuItemId,
            String itemName,
            String description,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {}
}
