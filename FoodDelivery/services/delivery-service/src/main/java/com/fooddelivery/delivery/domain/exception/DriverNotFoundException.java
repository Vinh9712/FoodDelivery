package com.fooddelivery.delivery.domain.exception;

import java.util.UUID;

public class DriverNotFoundException extends RuntimeException {
    public DriverNotFoundException(UUID id) {
        super("Driver not found: " + id);
    }

    public static DriverNotFoundException forUser(UUID userId) {
        return new DriverNotFoundException("No driver profile for user " + userId);
    }

    private DriverNotFoundException(String message) {
        super(message);
    }
}
