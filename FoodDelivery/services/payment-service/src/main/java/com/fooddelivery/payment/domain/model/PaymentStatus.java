package com.fooddelivery.payment.domain.model;

/**
 * Lifecycle states of a payment transaction.
 */
public enum PaymentStatus {
    /** Payment has been initiated but not yet processed. */
    PENDING,
    /** Payment was successfully completed. */
    COMPLETED,
    /** Payment processing failed (insufficient funds, gateway error, etc.). */
    FAILED,
    /** Payment was refunded after completion. */
    REFUNDED
}
