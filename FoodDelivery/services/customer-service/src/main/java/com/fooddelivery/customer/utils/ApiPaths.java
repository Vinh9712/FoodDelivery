package com.fooddelivery.customer.utils;

public final class ApiPaths {
    public static final String BASE = "/api/v1";

    // ── Auth ──────────────────────────────────────────────
    public static final String AUTH = BASE + "/auth";
    public static final String REGISTER = AUTH + "/register";
    public static final String LOGIN = AUTH + "/login";
    public static final String REFRESH_TOKEN = AUTH + "/refresh";
    public static final String LOGOUT = AUTH + "/logout";

    // ── Customers ─────────────────────────────────────────
    public static final String CUSTOMERS = BASE + "/customers";
    public static final String CUSTOMER_BY_ID = CUSTOMERS + "/{id}";
    public static final String CUSTOMER_PROFILE = CUSTOMERS + "/{id}/profile";
    public static final String CUSTOMER_ADDRESSES = CUSTOMERS + "/{id}/addresses";
    public static final String CUSTOMER_ADDRESS_BY_ID = CUSTOMERS + "/{id}/addresses/{addressId}";
    public static final String CUSTOMER_ADDRESS_DEFAULT = CUSTOMERS + "/{id}/addresses/{addressId}/default";

    private ApiPaths() {
    }
}
