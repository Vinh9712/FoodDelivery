package com.fooddelivery.customer.domain.exception;

import java.util.UUID;

public class CannotRemoveDefaultAddressException extends RuntimeException {
    public CannotRemoveDefaultAddressException(UUID addressId) {
        super("Cannot remove the default address when other addresses exist. Set another address as default first. Address ID: " + addressId);
    }
}
