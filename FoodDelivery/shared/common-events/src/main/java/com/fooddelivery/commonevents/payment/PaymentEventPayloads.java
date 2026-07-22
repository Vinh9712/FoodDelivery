package com.fooddelivery.commonevents.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentEventPayloads {
    public record PaymentSucceeded(UUID paymentId, UUID orderId, UUID customerId,
                                   String amount, String currency, Instant paidAt) {
        public PaymentSucceeded {
            required(paymentId, "paymentId"); required(orderId, "orderId"); required(customerId, "customerId");
            decimal(amount, "amount"); text(currency, "currency"); required(paidAt, "paidAt");
        }
    }

    public record PaymentFailed(UUID paymentId, UUID orderId, UUID customerId,
                                String amount, String currency, String reason, Instant failedAt) {
        public PaymentFailed {
            required(paymentId, "paymentId"); required(orderId, "orderId"); required(customerId, "customerId");
            decimal(amount, "amount"); text(currency, "currency"); text(reason, "reason"); required(failedAt, "failedAt");
        }
    }

    public record PaymentRefunded(UUID paymentId, UUID refundId, UUID orderId, UUID customerId,
                                  String amount, String currency, Instant refundedAt) {
        public PaymentRefunded {
            required(paymentId, "paymentId"); required(refundId, "refundId"); required(orderId, "orderId");
            required(customerId, "customerId"); decimal(amount, "amount"); text(currency, "currency"); required(refundedAt, "refundedAt");
        }
    }

    private static void required(Object value, String name) { if (value == null) throw new IllegalArgumentException(name + " is required"); }
    private static void text(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); }
    private static void decimal(String value, String name) {
        text(value, name);
        try {
            if (!new BigDecimal(value).toPlainString().equals(value)) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a canonical decimal string");
        }
    }
    private PaymentEventPayloads() {}
}
