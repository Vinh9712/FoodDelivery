// PaymentResponse.java
package com.fooddelivery.payment.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PaymentResponse(
        UUID orderId,
        String status,
        String transactionId,
        String message
) {}