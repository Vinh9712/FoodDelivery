package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.internal.RestaurantOwnershipResponse;
import com.fooddelivery.restaurant.application.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/restaurants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE')")
public class InternalRestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping("/{restaurantId}/ownership/{userId}")
    public RestaurantOwnershipResponse ownership(@PathVariable UUID restaurantId, @PathVariable UUID userId) {
        return new RestaurantOwnershipResponse(restaurantId, userId, restaurantService.isOwner(restaurantId, userId));
    }
}
