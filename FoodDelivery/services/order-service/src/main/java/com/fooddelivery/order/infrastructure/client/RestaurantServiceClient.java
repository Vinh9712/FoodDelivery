package com.fooddelivery.order.infrastructure.client;

import com.fooddelivery.order.infrastructure.client.dto.MenuQuoteRequest;
import com.fooddelivery.order.infrastructure.client.dto.MenuQuoteResponse;
import com.fooddelivery.order.infrastructure.client.dto.RestaurantOwnershipResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "restaurant-service")
public interface RestaurantServiceClient {

    @PostMapping(value = "/internal/v1/restaurants/{restaurantId}/menu/quote", headers = {"X-Internal-Service-Secret=${app.security.internal-service-secret:}"})
    MenuQuoteResponse quoteMenu(
            @PathVariable("restaurantId") UUID restaurantId,
            @RequestBody MenuQuoteRequest request);

    @GetMapping(value = "/internal/v1/restaurants/{restaurantId}/ownership/{userId}", headers = {"X-Internal-Service-Secret=${app.security.internal-service-secret:}"})
    RestaurantOwnershipResponse ownership(
            @PathVariable("restaurantId") UUID restaurantId,
            @PathVariable("userId") UUID userId);
}
