package com.fooddelivery.restaurant.api.dto.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record MenuQuoteRequest(
        @NotEmpty(message = "At least one menu item is required")
        @Size(max = 50, message = "An order cannot contain more than 50 item entries")
        @Valid
        List<Item> items
) {
    public record Item(
            @NotNull(message = "Menu item ID is required")
            UUID menuItemId,

            @Positive(message = "Quantity must be positive")
            @Max(value = 99, message = "Quantity cannot exceed 99")
            int quantity
    ) {}
}
