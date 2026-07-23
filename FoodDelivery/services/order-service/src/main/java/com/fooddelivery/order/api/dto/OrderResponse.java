package com.fooddelivery.order.api.dto;

import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for order create / get / list / cancel responses.
 */
public record OrderResponse(
        UUID id,
        UUID customerId,
        UUID restaurantId,
        OrderStatus status,
        BigDecimal totalAmount,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discountAmount,
        String note,
        List<OrderItemDto> items,
        DeliveryAddressDto deliveryAddress,
        AssignedDriverDto assignedDriver,
        PaymentStatus paymentStatus,
        RefundStatus refundStatus,
        CancellationCode cancellationCode,
        String cancellationReason,
        List<OrderStatusHistoryDto> statusHistory,
        Instant paidAt,
        Instant restaurantResponseDeadline,
        Instant createdAt,
        Instant updatedAt
) {
}
