package com.fooddelivery.customer.domain.exception;

import java.util.UUID;

public class RefreshTokenNotFoundException extends RuntimeException {
    public RefreshTokenNotFoundException(UUID tokenId) {
        super("Refresh token not found: " + tokenId);
    }
}
