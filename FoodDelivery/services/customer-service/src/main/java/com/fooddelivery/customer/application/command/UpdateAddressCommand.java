package com.fooddelivery.customer.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateAddressCommand(
    UUID userId,
    UUID addressId,
    String label,
    String addressLine,
    String district,
    String city,
    BigDecimal latitude,
    BigDecimal longitude,
    boolean defaultAddress
) {}
