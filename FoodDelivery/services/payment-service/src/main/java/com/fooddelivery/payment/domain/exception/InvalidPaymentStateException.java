package com.fooddelivery.payment.domain.exception;

import com.fooddelivery.payment.domain.model.valueobject.PaymentStatus;

public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(PaymentStatus status) {
        super("Invalid payment state: " + status);
    }
}
