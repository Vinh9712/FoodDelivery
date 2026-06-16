package com.fooddelivery.customer.application.command;

public record RefreshTokenCommand(
    String refreshToken,
    String deviceInfo,
    String ipAddress
) {}
