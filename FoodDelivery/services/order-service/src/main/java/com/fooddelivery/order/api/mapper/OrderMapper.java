package com.fooddelivery.order.api.mapper;

import com.fooddelivery.order.api.dto.AssignedDriverDto;
import com.fooddelivery.order.api.dto.OrderHistoryResponse;
import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.AssignedDriverInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps Order domain model to API DTOs.
 */
@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getAssignedDriver().map(this::toDriverDto).orElse(null),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    // ==================== NEW METHOD ====================

    /**
     * Map Order to OrderHistoryResponse
     */
    public OrderHistoryResponse toHistoryResponse(Order order) {
        return OrderHistoryResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .restaurantId(order.getRestaurantId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .discountAmount(order.getDiscountAmount())
                .paymentStatus(order.getPaymentStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private AssignedDriverDto toDriverDto(AssignedDriverInfo info) {
        return new AssignedDriverDto(
                info.driverId(),
                info.fullName(),
                info.phone(),
                info.vehicleType().name(),
                info.licensePlate(),
                info.avgRating(),
                info.assignedAt()
        );
    }
}