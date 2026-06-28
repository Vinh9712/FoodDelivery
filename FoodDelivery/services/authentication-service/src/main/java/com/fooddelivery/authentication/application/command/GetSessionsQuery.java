package com.fooddelivery.authentication.application.command;

import java.util.UUID;

public record GetSessionsQuery(
    UUID userId
) {}
