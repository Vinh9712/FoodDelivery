package com.fooddelivery.authentication.application.command;

public record RefreshTokenCommand(
    String refreshToken,
    String deviceInfo,
    String ipAddress
) {}
