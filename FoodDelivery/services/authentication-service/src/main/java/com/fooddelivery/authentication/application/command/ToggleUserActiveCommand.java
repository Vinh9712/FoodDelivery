package com.fooddelivery.authentication.application.command;

import java.util.UUID;

public record ToggleUserActiveCommand(
    UUID userId,
    boolean activate,
    UUID currentUserId
) {}
