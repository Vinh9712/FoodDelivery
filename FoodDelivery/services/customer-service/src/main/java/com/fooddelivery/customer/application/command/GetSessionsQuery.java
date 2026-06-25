package com.fooddelivery.customer.application.command;

import java.util.UUID;

public record GetSessionsQuery(
    UUID userId
) {}
