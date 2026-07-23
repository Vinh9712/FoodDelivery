package com.fooddelivery.order.api.dto;

import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OrderHistoryResponse {
    private UUID id;
    private UUID customerId;
    private UUID restaurantId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal discountAmount;
    private PaymentStatus paymentStatus;
    private Instant createdAt;
    private Instant updatedAt;
}