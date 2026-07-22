package com.fooddelivery.order.infrastructure.client;

import com.fooddelivery.order.infrastructure.client.dto.DeliveryRequest;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryResponse;
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

    @GetMapping("/internal/v1/deliveries/orders/{orderId}")
    DeliveryResponse findByOrderId(@PathVariable("orderId") UUID orderId);
}
