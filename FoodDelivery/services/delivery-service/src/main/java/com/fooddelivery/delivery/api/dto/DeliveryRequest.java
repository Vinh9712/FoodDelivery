package com.fooddelivery.delivery.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal schedule request — immutable pickup/dropoff snapshots from READY_FOR_PICKUP.
 */
public record DeliveryRequest(
        UUID orderId,
        UUID customerId,
        UUID restaurantId,
        PickupAddressSnapshot pickupAddressSnapshot,
        DropoffAddressSnapshot dropoffAddressSnapshot
) {
    public record PickupAddressSnapshot(
            UUID restaurantId,
            String name,
            String phone,
            String addressText,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record DropoffAddressSnapshot(
            String addressLine,
            String district,
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
