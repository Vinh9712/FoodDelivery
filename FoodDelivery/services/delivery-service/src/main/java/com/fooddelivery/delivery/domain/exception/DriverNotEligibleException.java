package com.fooddelivery.delivery.domain.exception;

import com.fooddelivery.delivery.domain.model.valueobject.DriverStatus;
import java.util.UUID;

public class DriverNotEligibleException extends RuntimeException {
    public DriverNotEligibleException(UUID driverId, DriverStatus status) {
        super("Driver " + driverId + " is not eligible (status: " + status + ")");
    }
}
