package com.fooddelivery.payment.domain.model.valueobject;

import com.fasterxml.jackson.databind.JsonNode;

public record CachedResponse(JsonNode data) {
    public CachedResponse {
        if (data == null) {
            throw new IllegalArgumentException("Cached response data cannot be null");
        }
    }
}
