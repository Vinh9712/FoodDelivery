package com.fooddelivery.commonevents.order;

import java.time.Instant;
import java.util.UUID;

public final class OrderEventPayloads {
    public enum Source { PAYMENT_RECONCILIATION, RESTAURANT, DELIVERY_EVENT, DELIVERY_RECONCILIATION, COMPENSATION, SYSTEM_TIMEOUT }

    public record OrderCreated(UUID orderId, UUID customerId, UUID restaurantId,
                               String totalAmount, String currency, Instant createdAt) {
        public OrderCreated {
            required(orderId, "orderId"); required(customerId, "customerId"); required(restaurantId, "restaurantId");
            decimal(totalAmount, "totalAmount"); text(currency, "currency"); required(createdAt, "createdAt");
        }
    }

    public record OrderStatusChanged(UUID orderId, UUID customerId, UUID restaurantId,
                                     String fromStatus, String toStatus, Source source, Instant changedAt) {
        public OrderStatusChanged {
            required(orderId, "orderId"); required(customerId, "customerId"); required(restaurantId, "restaurantId");
            text(fromStatus, "fromStatus"); text(toStatus, "toStatus"); required(source, "source"); required(changedAt, "changedAt");
        }
    }

    public record OrderCancelled(UUID orderId, UUID customerId, UUID restaurantId,
                                 String cancellationCode, String reason, String paymentStatus,
                                 String refundStatus, Instant cancelledAt) {
        public OrderCancelled {
            required(orderId, "orderId"); required(customerId, "customerId"); required(restaurantId, "restaurantId");
            text(cancellationCode, "cancellationCode"); text(reason, "reason"); text(paymentStatus, "paymentStatus");
            text(refundStatus, "refundStatus"); required(cancelledAt, "cancelledAt");
        }
    }

    private static void required(Object value, String name) { if (value == null) throw new IllegalArgumentException(name + " is required"); }
    private static void text(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); }
    private static void decimal(String value, String name) { text(value, name); try { if (!new java.math.BigDecimal(value).toPlainString().equals(value)) throw new NumberFormatException(); } catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " must be a canonical decimal string"); } }
    private OrderEventPayloads() {}
}
