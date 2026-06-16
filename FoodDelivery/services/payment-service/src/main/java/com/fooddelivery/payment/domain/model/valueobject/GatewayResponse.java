package com.fooddelivery.payment.domain.model.valueobject;

import com.fasterxml.jackson.databind.JsonNode;

public record GatewayResponse(JsonNode rawData) {
    public GatewayResponse {
        if (rawData == null) {
            throw new IllegalArgumentException("Raw gateway response data cannot be null");
        }
    }
}
