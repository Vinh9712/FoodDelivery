package com.fooddelivery.customer.config;

import java.util.UUID;

public record UserPrincipal(UUID userId, String email, String role) {}
