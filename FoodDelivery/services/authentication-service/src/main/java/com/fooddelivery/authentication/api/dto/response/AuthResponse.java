package com.fooddelivery.authentication.api.dto.response;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn
) {}
