package com.fooddelivery.order.domain.model.valueobject;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PickupAddressSnapshot(
        UUID restaurantId,
        String name,
        String phone,
        String addressText,
        BigDecimal latitude,
        BigDecimal longitude) {

    public PickupAddressSnapshot {
        Objects.requireNonNull(restaurantId, "restaurantId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("phone is required");
        if (addressText == null || addressText.isBlank()) throw new IllegalArgumentException("addressText is required");
    }
}
