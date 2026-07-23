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
        DropoffAddressSnapshot dropoffAddressSnapshot,
        /** Optional snapshot for driver UI (ignored by schedule hash). */
        String customerName,
        String customerPhone
) {
    /** Backward-compatible ctor without contact snapshot. */
    public DeliveryRequest(
            UUID orderId,
            UUID customerId,
            UUID restaurantId,
            PickupAddressSnapshot pickupAddressSnapshot,
            DropoffAddressSnapshot dropoffAddressSnapshot) {
        this(orderId, customerId, restaurantId, pickupAddressSnapshot, dropoffAddressSnapshot, null, null);
    }
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
