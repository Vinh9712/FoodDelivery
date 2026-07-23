package com.fooddelivery.customer.application.command;

import java.util.UUID;

public record UpdateProfileCommand(
    UUID authUserId,
    String fullName,
    String phone,
    String avatarUrl
) {}
