package com.fooddelivery.delivery.api.dto;

import java.math.BigDecimal;

public record LocationUpdateRequest(
        BigDecimal latitude,
        BigDecimal longitude
) {}
