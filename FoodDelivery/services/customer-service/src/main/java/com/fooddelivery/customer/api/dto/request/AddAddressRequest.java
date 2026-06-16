package com.fooddelivery.customer.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddAddressRequest(
    @Size(max = 50, message = "Label must be at most 50 characters")
    String label,

    @NotBlank(message = "Address line is required")
    String addressLine,

    @Size(max = 100, message = "District must be at most 100 characters")
    String district,

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must be at most 100 characters")
    String city,

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    BigDecimal latitude,

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    BigDecimal longitude,

    boolean defaultAddress
) {}
