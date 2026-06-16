package com.fooddelivery.customer.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record AddressResponse(
    UUID id,
    String label,
    String addressLine,
    String district,
    String city,
    BigDecimal latitude,
    BigDecimal longitude,
    boolean defaultAddress
) {}
