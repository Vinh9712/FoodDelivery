// RefundRequest.java
package com.fooddelivery.payment.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record RefundRequest(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @Positive(message = "Amount must be greater than 0")
        BigDecimal amount,

        String reason
) {}