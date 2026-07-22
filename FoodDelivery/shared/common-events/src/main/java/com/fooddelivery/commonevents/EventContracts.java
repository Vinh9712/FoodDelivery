package com.fooddelivery.commonevents;

public final class EventContracts {
    public static final String AUTH_EVENTS_TOPIC = "auth-events";
    public static final String USER_REGISTERED = "user.registered";
    public static final String ORDER_EVENTS_V1 = "order.events.v1";
    public static final String DELIVERY_EVENTS_V1 = "delivery.events.v1";
    public static final String PAYMENT_EVENTS_V1 = "payment.events.v1";
    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_STATUS_CHANGED = "OrderStatusChanged";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String DRIVER_ASSIGNED = "DriverAssigned";
    public static final String DELIVERY_PICKED_UP = "DeliveryPickedUp";
    public static final String DELIVERY_IN_TRANSIT = "DeliveryInTransit";
    public static final String DELIVERY_COMPLETED = "DeliveryCompleted";
    public static final String DELIVERY_FAILED = "DeliveryFailed";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";

    private EventContracts() {}
}
