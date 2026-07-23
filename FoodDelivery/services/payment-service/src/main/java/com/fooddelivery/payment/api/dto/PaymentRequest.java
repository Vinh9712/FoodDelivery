// PaymentRequest.java
package com.fooddelivery.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PaymentRequest(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @Positive(message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotBlank(message = "Payment method is required")
        String paymentMethod,

        String description,
        String returnUrl,
        String cancelUrl
) {}