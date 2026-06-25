package com.fooddelivery.order.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO cho tạo đơn hàng mới.
 * Sử dụng Java 21 Record + nested record cho items.
 */
public record CreateOrderRequest(
        UUID customerId,
        UUID restaurantId,
        String deliveryAddress,
        BigDecimal deliveryFee,
        BigDecimal discountAmount,
        List<OrderItemRequest> items
) {
    /**
     * Một dòng hàng trong đơn đặt hàng.
     */
    public record OrderItemRequest(
            UUID menuItemId,
            String itemName,
            String description,
            BigDecimal unitPrice,
            int quantity
    ) {}
}
