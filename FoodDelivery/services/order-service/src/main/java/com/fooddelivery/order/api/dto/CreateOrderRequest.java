package com.fooddelivery.order.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO cho tạo đơn hàng mới.
 * Sử dụng Java 21 Record + nested record cho items.
 */
public record CreateOrderRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Restaurant ID is required")
        UUID restaurantId,

        @NotNull(message = "Total amount is required")
        @Positive(message = "Total amount must be positive")
        BigDecimal totalAmount,

        String currency
        String deliveryAddress,
        BigDecimal deliveryFee,
        BigDecimal discountAmount,
        List<OrderItemRequest> items
) {
    /**
     * Compact constructor: default currency to VND if not supplied.
     * Một dòng hàng trong đơn đặt hàng.
     */
    public CreateOrderRequest {
        if (currency == null || currency.isBlank()) {
            currency = "VND";
        }
    }
    public record OrderItemRequest(
            UUID menuItemId,
            String itemName,
            String description,
            BigDecimal unitPrice,
            int quantity
    ) {}
}
