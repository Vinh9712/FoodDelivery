package com.fooddelivery.customer.application.command;

import java.util.UUID;

public record SetDefaultAddressCommand(
        UUID authUserId,
        UUID addressId
) {}
