package com.fooddelivery.order.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for creating a new order via {@code POST /api/v1/orders}.
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
) {
    /**
     * Compact constructor: default currency to VND if not supplied.
     */
    public CreateOrderRequest {
        if (currency == null || currency.isBlank()) {
            currency = "VND";
        }
    }
}
