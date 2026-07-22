package com.fooddelivery.order.infrastructure.client;

import com.fooddelivery.order.infrastructure.client.dto.DeliveryRequest;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryResponse;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/**
 * Declarative HTTP client for Delivery Service internal schedule APIs.
 */
@FeignClient(name = "delivery-service")
public interface DeliveryServiceClient {

    @PostMapping("/internal/v1/deliveries")
    DeliveryResponse schedule(
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody DeliveryRequest request);

    /**
     * Lookup remote delivery truth before schedule retry. Extra lifecycle timestamps
     * deserialize when present; otherwise null (catch-up uses available fields).
     */
    @GetMapping("/internal/v1/deliveries/orders/{orderId}")
    DeliveryStatusResponse findByOrderId(@PathVariable("orderId") UUID orderId);
}
