package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.RestaurantRequest;
import com.fooddelivery.restaurant.api.dto.RestaurantResponse;
import com.fooddelivery.restaurant.api.dto.RestaurantSearchRequest;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface RestaurantService {
    RestaurantResponse createRestaurant(RestaurantRequest request);
    RestaurantResponse updateRestaurant(UUID id, RestaurantRequest request);
    void deleteRestaurant(UUID id);
    RestaurantResponse getRestaurantById(UUID id);
    List<RestaurantResponse> getAllRestaurants();
    List<RestaurantResponse> getRestaurantsByOwnerId(UUID ownerId);
    Page<RestaurantResponse> searchRestaurants(RestaurantSearchRequest request);
    RestaurantResponse setAvailability(UUID id, boolean accepting);
    RestaurantResponse changeStatus(UUID id, RestaurantStatus status);
    boolean isOwner(UUID restaurantId, UUID userId);
}
