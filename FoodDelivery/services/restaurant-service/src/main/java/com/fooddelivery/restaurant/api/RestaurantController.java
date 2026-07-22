package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.RestaurantRequest;
import com.fooddelivery.restaurant.api.dto.RestaurantResponse;
import com.fooddelivery.restaurant.api.dto.RestaurantSearchRequest;
import com.fooddelivery.restaurant.api.dto.RestaurantAvailabilityRequest;
import com.fooddelivery.restaurant.api.dto.RestaurantStatusRequest;
import com.fooddelivery.restaurant.application.RestaurantService;
import com.fooddelivery.restaurant.domain.RestaurantStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@Slf4j
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<RestaurantResponse> createRestaurant(
            @Valid @RequestBody RestaurantRequest request,
            Authentication authentication) {
        log.info("POST /api/v1/restaurants - Create restaurant");
        if (isAdmin(authentication) && request.getOwnerId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Owner ID is required for admin-created restaurants");
        }
        if (!isAdmin(authentication)) {
            request.setOwnerId(UUID.fromString(authentication.getName()));
        }
        RestaurantResponse response = restaurantService.createRestaurant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RestaurantResponse>> getAllRestaurants() {
        log.info("GET /api/v1/restaurants - Get all restaurants");
        List<RestaurantResponse> responses = restaurantService.getAllRestaurants();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> getRestaurantById(@PathVariable("id") UUID id) {
        log.info("GET /api/v1/restaurants/{} - Get restaurant by ID", id);
        RestaurantResponse response = restaurantService.getRestaurantById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@restaurantAuthorization.canManageRestaurant(#id, authentication)")
    public ResponseEntity<RestaurantResponse> updateRestaurant(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RestaurantRequest request) {
        log.info("PUT /api/v1/restaurants/{} - Update restaurant", id);
        RestaurantResponse response = restaurantService.updateRestaurant(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@restaurantAuthorization.canManageRestaurant(#id, authentication)")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable("id") UUID id) {
        log.info("DELETE /api/v1/restaurants/{} - Delete restaurant", id);
        restaurantService.deleteRestaurant(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/availability")
    @PreAuthorize("@restaurantAuthorization.canManageRestaurant(#id, authentication)")
    public ResponseEntity<RestaurantResponse> setAvailability(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RestaurantAvailabilityRequest request) {
        return ResponseEntity.ok(restaurantService.setAvailability(id, request.accepting()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestaurantResponse> changeStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RestaurantStatusRequest request) {
        return ResponseEntity.ok(restaurantService.changeStatus(id, request.status()));
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<RestaurantResponse>> getRestaurantsByOwnerId(@PathVariable("ownerId") UUID ownerId) {
        log.info("GET /api/v1/restaurants/owner/{} - Get restaurants by owner", ownerId);
        List<RestaurantResponse> responses = restaurantService.getRestaurantsByOwnerId(ownerId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<RestaurantResponse>> searchRestaurants(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "district", required = false) String district,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "minRating", required = false) BigDecimal minRating,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        log.info("GET /api/v1/restaurants/search - Search restaurants");

        RestaurantSearchRequest request = RestaurantSearchRequest.builder()
                .name(name)
                .city(city)
                .district(district)
                .status(status)
                .minRating(minRating)
                .page(page)
                .size(size)
                .build();

        Page<RestaurantResponse> responses = restaurantService.searchRestaurants(request);
        return ResponseEntity.ok(responses);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

}
