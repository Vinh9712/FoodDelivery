package com.fooddelivery.payment.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO cho hoàn tiền.
 */
public record RefundRequest(
        UUID orderId,
        BigDecimal amount
) {}
