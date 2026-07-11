package com.fooddelivery.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO cho hoàn tiền.
 */
public record RefundRequest(
        @NotNull
        UUID orderId,

        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount
) {}
