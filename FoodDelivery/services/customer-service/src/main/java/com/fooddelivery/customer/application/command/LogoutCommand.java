package com.fooddelivery.customer.application.command;

public record LogoutCommand(
    String refreshToken
) {}
