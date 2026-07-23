package com.fooddelivery.order.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderPreviewResponse(
        UUID restaurantId,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        List<Line> items
) {
    public record Line(
            UUID menuItemId,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {
    }
}
