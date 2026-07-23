package com.fooddelivery.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {
}
