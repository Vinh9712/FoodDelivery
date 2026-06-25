package com.fooddelivery.customer.domain.exception;

import java.util.UUID;

public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(UUID addressId) {
        super("Address not found: " + addressId);
    }
}
