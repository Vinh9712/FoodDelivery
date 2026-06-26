package com.fooddelivery.payment.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO cho thanh toán đơn hàng.
 */
public record PaymentRequest(
        UUID orderId,
        UUID customerId,
        BigDecimal amount
) {}
