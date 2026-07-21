package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.RestaurantResponse;
import com.fooddelivery.restaurant.application.RestaurantServiceWithResilience;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/resilience")
@RequiredArgsConstructor
@Slf4j
public class ResilienceController {

    private final RestaurantServiceWithResilience restaurantServiceWithResilience;

    @GetMapping("/circuit-breaker/{id}")
    public ResponseEntity<RestaurantResponse> testCircuitBreaker(@PathVariable("id") UUID id) {
        log.info("Testing Circuit Breaker for restaurant: {}", id);
        RestaurantResponse response = restaurantServiceWithResilience.getRestaurantWithCircuitBreaker(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/retry/{id}")
    public ResponseEntity<RestaurantResponse> testRetry(@PathVariable("id") UUID id) {
        log.info("Testing Retry for restaurant: {}", id);
        RestaurantResponse response = restaurantServiceWithResilience.getRestaurantWithRetry(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/timeout/{id}")
    public ResponseEntity<RestaurantResponse> testTimeout(@PathVariable("id") UUID id) throws ExecutionException, InterruptedException {
        log.info("Testing Timeout for restaurant: {}", id);
        CompletableFuture<RestaurantResponse> future = restaurantServiceWithResilience.getRestaurantWithTimeout(id);
        RestaurantResponse response = future.get();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all/{id}")
    public ResponseEntity<RestaurantResponse> testAll(@PathVariable("id") UUID id) throws ExecutionException, InterruptedException {
        log.info("Testing ALL Resilience patterns for restaurant: {}", id);
        CompletableFuture<RestaurantResponse> future = restaurantServiceWithResilience.getRestaurantWithAllPatterns(id);
        RestaurantResponse response = future.get();
        return ResponseEntity.ok(response);
    }
}