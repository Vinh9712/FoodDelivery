package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.MenuResponse;
import com.fooddelivery.restaurant.application.MenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/restaurants/{restaurantId}/menu")
    public ResponseEntity<MenuResponse> getMenuByRestaurantId(
            @PathVariable("restaurantId") UUID restaurantId) {
        log.info("GET /restaurants/{}/menu - Get full menu", restaurantId);
        MenuResponse response = menuService.getMenuByRestaurantId(restaurantId);
        return ResponseEntity.ok(response);
    }
}