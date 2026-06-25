package com.fooddelivery.customer.application.command;

import java.util.UUID;

public record RevokeSessionCommand(
    UUID sessionId,
    UUID userId
) {}
