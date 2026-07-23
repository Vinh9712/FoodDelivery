package com.fooddelivery.authentication.application.command;

import java.util.UUID;

public record GetUserDetailQuery(
    UUID userId
) {}
