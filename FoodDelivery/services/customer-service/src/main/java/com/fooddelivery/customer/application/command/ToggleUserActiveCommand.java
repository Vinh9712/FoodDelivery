package com.fooddelivery.customer.application.command;

import java.util.UUID;

public record ToggleUserActiveCommand(
    UUID userId,
    boolean activate,
    UUID currentUserId
) {}
