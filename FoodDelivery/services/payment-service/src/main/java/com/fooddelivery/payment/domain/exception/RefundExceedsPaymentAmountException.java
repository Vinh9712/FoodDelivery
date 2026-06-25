package com.fooddelivery.payment.domain.exception;

import java.util.UUID;

public class RefundExceedsPaymentAmountException extends RuntimeException {
    public RefundExceedsPaymentAmountException(UUID paymentId) {
        super("Refund amount exceeds the remaining paid amount on payment: " + paymentId);
    }
}
