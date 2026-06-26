package com.fooddelivery.order.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Request gửi đến Payment Service để thanh toán đơn hàng. */
public record PaymentRequest(
        UUID orderId,
        UUID customerId,
        BigDecimal amount
) {}
