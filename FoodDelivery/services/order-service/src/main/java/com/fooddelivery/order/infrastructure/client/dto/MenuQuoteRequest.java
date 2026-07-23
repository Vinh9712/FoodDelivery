package com.fooddelivery.order.infrastructure.client.dto;

import java.util.List;
import java.util.UUID;

public record MenuQuoteRequest(List<Item> items) {
    public record Item(UUID menuItemId, int quantity) {}
}
