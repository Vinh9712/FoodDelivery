package com.fooddelivery.customer.application.command;

import java.util.UUID;

public record UpdateProfileCommand(
    UUID userId,
    String fullName,
    String phone,
    String avatarUrl
) {}
