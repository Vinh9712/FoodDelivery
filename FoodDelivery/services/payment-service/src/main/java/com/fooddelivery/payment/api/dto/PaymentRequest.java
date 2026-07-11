package com.fooddelivery.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO cho thanh toán đơn hàng.
 */
public record PaymentRequest(
        @NotNull
        UUID orderId,

        @NotNull
        UUID customerId,

        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount
) {}
