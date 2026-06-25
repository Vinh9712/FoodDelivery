package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.MenuCategoryRequest;
import com.fooddelivery.restaurant.api.dto.MenuCategoryResponse;
import com.fooddelivery.restaurant.application.MenuCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class MenuCategoryController {

    private final MenuCategoryService menuCategoryService;

    @PostMapping("/restaurants/{restaurantId}/categories")
    public ResponseEntity<MenuCategoryResponse> createCategory(
            @PathVariable("restaurantId") UUID restaurantId,
            @Valid @RequestBody MenuCategoryRequest request) {
        log.info("POST /restaurants/{}/categories - Create category", restaurantId);
        MenuCategoryResponse response = menuCategoryService.createCategory(restaurantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/restaurants/{restaurantId}/categories")
    public ResponseEntity<List<MenuCategoryResponse>> getCategoriesByRestaurant(
            @PathVariable("restaurantId") UUID restaurantId) {
        log.info("GET /restaurants/{}/categories - Get categories", restaurantId);
        List<MenuCategoryResponse> responses = menuCategoryService.getCategoriesByRestaurant(restaurantId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<MenuCategoryResponse> getCategoryById(
            @PathVariable("categoryId") UUID categoryId) {
        log.info("GET /categories/{} - Get category", categoryId);
        MenuCategoryResponse response = menuCategoryService.getCategoryById(categoryId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<MenuCategoryResponse> updateCategory(
            @PathVariable("categoryId") UUID categoryId,
            @Valid @RequestBody MenuCategoryRequest request) {
        log.info("PUT /categories/{} - Update category", categoryId);
        MenuCategoryResponse response = menuCategoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable("categoryId") UUID categoryId) {
        log.info("DELETE /categories/{} - Delete category", categoryId);
        menuCategoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}