package com.fooddelivery.payment.domain.exception;

public class IdempotencyKeyAlreadyUsedException extends RuntimeException {
    public IdempotencyKeyAlreadyUsedException(String key) {
        super("Idempotency key has already been used for another request: " + key);
    }
}
