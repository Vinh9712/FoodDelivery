package com.fooddelivery.customer.application.command;

public record LoginCommand(
    String email,
    String password,
    String deviceInfo,
    String ipAddress
) {}
