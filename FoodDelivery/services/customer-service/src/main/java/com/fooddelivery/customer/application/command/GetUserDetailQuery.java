package com.fooddelivery.customer.application.command;

import java.util.UUID;

public record GetUserDetailQuery(
    UUID userId
) {}
