package com.fooddelivery.authentication.config;

import java.util.UUID;

public record UserPrincipal(UUID userId, String email, String role) {}
