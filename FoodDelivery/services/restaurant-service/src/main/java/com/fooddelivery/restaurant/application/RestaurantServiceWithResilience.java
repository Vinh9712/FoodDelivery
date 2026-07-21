package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.RestaurantResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantServiceWithResilience {

    private final RestaurantService restaurantService;

    // ============================================================
    // 1. CIRCUIT BREAKER: Bảo vệ khi service gặp lỗi
    // ============================================================
    @CircuitBreaker(name = "restaurantService", fallbackMethod = "fallbackGetRestaurant")
    public RestaurantResponse getRestaurantWithCircuitBreaker(UUID id) {
        log.info("Calling getRestaurant with Circuit Breaker");
        return restaurantService.getRestaurantById(id);
    }

    // Fallback method khi Circuit Breaker mở
    public RestaurantResponse fallbackGetRestaurant(UUID id, Throwable t) {
        log.warn("Circuit Breaker OPEN - Fallback for restaurant: {}", id);
        return RestaurantResponse.builder()
                .id(id)
                .name("[FALLBACK] Restaurant temporarily unavailable")
                .description("Service is currently unavailable. Please try again later.")
                .build();
    }

    // ============================================================
    // 2. RETRY: Tự động thử lại khi gặp lỗi
    // ============================================================
    @Retry(name = "restaurantService", fallbackMethod = "fallbackGetRestaurant")
    public RestaurantResponse getRestaurantWithRetry(UUID id) {
        log.info("Calling getRestaurant with Retry");
        return restaurantService.getRestaurantById(id);
    }

    // ============================================================
    // 3. TIMEOUT: Giới hạn thời gian chờ
    // ============================================================
    @TimeLimiter(name = "restaurantService")
    @CircuitBreaker(name = "restaurantService")
    public CompletableFuture<RestaurantResponse> getRestaurantWithTimeout(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Calling getRestaurant with Timeout");
            return restaurantService.getRestaurantById(id);
        });
    }

    // ============================================================
    // 4. Kết hợp tất cả: Circuit Breaker + Retry + Timeout
    // ============================================================
    @CircuitBreaker(name = "restaurantService", fallbackMethod = "fallbackGetRestaurant")
    @Retry(name = "restaurantService")
    @TimeLimiter(name = "restaurantService")
    public CompletableFuture<RestaurantResponse> getRestaurantWithAllPatterns(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Calling getRestaurant with ALL Resilience patterns");
            return restaurantService.getRestaurantById(id);
        });
    }
}