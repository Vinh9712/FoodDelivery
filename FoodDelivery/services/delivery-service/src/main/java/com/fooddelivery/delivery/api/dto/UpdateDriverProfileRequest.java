package com.fooddelivery.delivery.api.dto;

import com.fooddelivery.delivery.domain.model.valueobject.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDriverProfileRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 20) String phone,
        @NotNull VehicleType vehicleType,
        @NotBlank @Size(max = 20) String licensePlate
) {
}
