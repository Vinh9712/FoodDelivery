package com.fooddelivery.order.api.dto;

import java.math.BigDecimal;

public record DeliveryAddressDto(
        String addressLine,
        String district,
        String city,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
