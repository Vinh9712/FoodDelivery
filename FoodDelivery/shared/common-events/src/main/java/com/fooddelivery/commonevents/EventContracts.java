package com.fooddelivery.commonevents;

public final class EventContracts {

    public static final String AUTH_EVENTS_TOPIC = "auth-events";
    public static final String CUSTOMER_EVENTS_TOPIC = "customer-events";

    public static final String USER_REGISTERED = "user.registered";
    public static final String CUSTOMER_CREATED = "customer.created";
    public static final String CUSTOMER_UPDATED = "customer.updated";

    private EventContracts() {}
}
