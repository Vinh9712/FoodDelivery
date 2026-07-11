package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.MenuItemRequest;
import com.fooddelivery.restaurant.api.dto.MenuItemResponse;
import com.fooddelivery.restaurant.application.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class MenuItemController {

    private final MenuItemService menuItemService;

    @PostMapping("/restaurants/{restaurantId}/items")
    @PreAuthorize("@restaurantAuthorization.canManageRestaurant(#restaurantId, authentication)")
    public ResponseEntity<MenuItemResponse> createMenuItem(
            @PathVariable("restaurantId") UUID restaurantId,
            @Valid @RequestBody MenuItemRequest request) {
        log.info("POST /restaurants/{}/items - Create menu item", restaurantId);
        MenuItemResponse response = menuItemService.createMenuItem(restaurantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/restaurants/{restaurantId}/items")
    public ResponseEntity<List<MenuItemResponse>> getMenuItemsByRestaurant(
            @PathVariable("restaurantId") UUID restaurantId) {
        log.info("GET /restaurants/{}/items - Get menu items", restaurantId);
        List<MenuItemResponse> responses = menuItemService.getMenuItemsByRestaurant(restaurantId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<MenuItemResponse> getMenuItemById(
            @PathVariable("itemId") UUID itemId) {
        log.info("GET /items/{} - Get menu item", itemId);
        MenuItemResponse response = menuItemService.getMenuItemById(itemId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("@restaurantAuthorization.canManageItem(#itemId, authentication)")
    public ResponseEntity<MenuItemResponse> updateMenuItem(
            @PathVariable("itemId") UUID itemId,
            @Valid @RequestBody MenuItemRequest request) {
        log.info("PUT /items/{} - Update menu item", itemId);
        MenuItemResponse response = menuItemService.updateMenuItem(itemId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("@restaurantAuthorization.canManageItem(#itemId, authentication)")
    public ResponseEntity<Void> deleteMenuItem(
            @PathVariable("itemId") UUID itemId) {
        log.info("DELETE /items/{} - Delete menu item", itemId);
        menuItemService.deleteMenuItem(itemId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/items/{itemId}/availability")
    @PreAuthorize("@restaurantAuthorization.canManageItem(#itemId, authentication)")
    public ResponseEntity<MenuItemResponse> updateAvailability(
            @PathVariable("itemId") UUID itemId,
            @RequestParam("isAvailable") Boolean isAvailable) {
        log.info("PATCH /items/{}/availability?isAvailable={}", itemId, isAvailable);
        MenuItemResponse response = menuItemService.updateAvailability(itemId, isAvailable);
        return ResponseEntity.ok(response);
    }
}
