package com.fooddelivery.authentication.application.command;

import java.util.UUID;

public record RevokeOthersCommand(
    UUID userId,
    UUID currentSessionId
) {}
