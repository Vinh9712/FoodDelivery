package com.fooddelivery.delivery.api.dto;

import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record DeliveryDetailResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        String customerName,
        String customerPhone,
        UUID driverId,
        DeliveryStatus status,
        String dropoffAddress,
        String pickupAddress,
        Instant driverAssignedAt,
        Instant pickedUpAt,
        Instant deliveredAt,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeliveryDetailResponse from(Delivery delivery) {
        return new DeliveryDetailResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCustomerId(),
                delivery.getCustomerName(),
                delivery.getCustomerPhone(),
                delivery.getDriverId(),
                delivery.getStatus(),
                delivery.getDropoffAddress() == null ? null : delivery.getDropoffAddress().text(),
                delivery.getPickupAddress() == null ? null : delivery.getPickupAddress().text(),
                delivery.getDriverAssignedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getFailureReason(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt());
    }
}
