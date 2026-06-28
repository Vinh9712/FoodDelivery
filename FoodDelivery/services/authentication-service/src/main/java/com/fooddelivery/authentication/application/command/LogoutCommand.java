package com.fooddelivery.authentication.application.command;

public record LogoutCommand(
    String refreshToken
) {}
