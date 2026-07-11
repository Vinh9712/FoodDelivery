package com.fooddelivery.restaurant.infrastructure.client;

import com.fooddelivery.restaurant.infrastructure.client.dto.ReviewEligibilityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderServiceClient {

    @GetMapping("/internal/v1/orders/{orderId}/review-eligibility")
    ReviewEligibilityResponse getReviewEligibility(
            @PathVariable("orderId") UUID orderId,
            @RequestParam("customerId") UUID customerId,
            @RequestParam("restaurantId") UUID restaurantId);
}
