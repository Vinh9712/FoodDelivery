package com.fooddelivery.order.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Request gửi đến Payment Service để hoàn tiền. */
public record RefundRequest(
        UUID orderId,
        BigDecimal amount
) {}
