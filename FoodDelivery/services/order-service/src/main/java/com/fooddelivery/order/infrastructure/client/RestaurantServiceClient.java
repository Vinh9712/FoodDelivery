package com.fooddelivery.order.infrastructure.client;

import com.fooddelivery.order.infrastructure.client.dto.MenuQuoteRequest;
import com.fooddelivery.order.infrastructure.client.dto.MenuQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "restaurant-service")
public interface RestaurantServiceClient {

    @PostMapping("/internal/v1/restaurants/{restaurantId}/menu/quote")
    MenuQuoteResponse quoteMenu(
            @PathVariable("restaurantId") UUID restaurantId,
            @RequestBody MenuQuoteRequest request);
}
