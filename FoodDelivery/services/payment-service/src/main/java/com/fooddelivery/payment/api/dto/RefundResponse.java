// RefundResponse.java
package com.fooddelivery.payment.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record RefundResponse(
        UUID orderId,
        String status,
        String message
) {}