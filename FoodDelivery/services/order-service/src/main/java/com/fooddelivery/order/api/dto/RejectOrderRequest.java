package com.fooddelivery.order.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectOrderRequest(
        @NotBlank String reason
) {
}
