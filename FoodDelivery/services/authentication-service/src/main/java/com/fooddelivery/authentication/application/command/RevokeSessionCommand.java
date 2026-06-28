package com.fooddelivery.authentication.application.command;

import java.util.UUID;

public record RevokeSessionCommand(
    UUID sessionId,
    UUID userId
) {}
