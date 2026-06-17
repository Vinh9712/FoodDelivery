package com.fooddelivery.customer.application.command;

import java.util.UUID;

public record RemoveAddressCommand(
    UUID userId,
    UUID addressId
) {}
