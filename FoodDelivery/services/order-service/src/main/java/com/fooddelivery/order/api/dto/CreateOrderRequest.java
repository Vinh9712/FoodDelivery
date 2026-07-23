package com.fooddelivery.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO cho tạo đơn hàng mới.
 * Sử dụng Java 21 Record + nested record cho items.
 */
public record CreateOrderRequest(
        @NotNull(message = "Restaurant ID is required")
        UUID restaurantId,

        @NotBlank(message = "Delivery address is required")
        String deliveryAddress,

        @NotEmpty(message = "At least one order item is required")
        @Size(max = 50, message = "An order cannot contain more than 50 item entries")
        @Valid
        List<OrderItemRequest> items,

        /** Optional delivery note / special instructions. */
        @Size(max = 500, message = "Note cannot exceed 500 characters")
        String note
) {
    public CreateOrderRequest {
        if (note != null && note.isBlank()) {
            note = null;
        }
    }

    /** Backward-compatible constructor without note. */
    public CreateOrderRequest(UUID restaurantId, String deliveryAddress, List<OrderItemRequest> items) {
        this(restaurantId, deliveryAddress, items, null);
    }

    public record OrderItemRequest(
            @NotNull(message = "Menu item ID is required")
            UUID menuItemId,

            @Positive(message = "Quantity must be positive")
            @Max(value = 99, message = "Quantity cannot exceed 99")
            int quantity
    ) {}
}
