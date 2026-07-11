package com.fooddelivery.payment.domain.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID orderId) {
        super("Payment not found for order: " + orderId);
    }
}
