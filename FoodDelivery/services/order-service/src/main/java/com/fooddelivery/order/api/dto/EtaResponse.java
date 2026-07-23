package com.fooddelivery.order.api.dto;

import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class EtaResponse {
    private UUID orderId;
    private int estimatedMinutes;
    private OrderStatus status;
}