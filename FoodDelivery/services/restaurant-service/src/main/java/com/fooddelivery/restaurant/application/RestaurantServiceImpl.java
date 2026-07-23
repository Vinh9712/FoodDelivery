package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.RestaurantRequest;
import com.fooddelivery.restaurant.api.dto.RestaurantResponse;
import com.fooddelivery.restaurant.api.dto.RestaurantSearchRequest;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import com.fooddelivery.restaurant.domain.RestaurantStatus;
import com.fooddelivery.restaurant.exception.RestaurantNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RestaurantServiceImpl implements RestaurantService {

    private final RestaurantRepository restaurantRepository;

    @Override
    public RestaurantResponse createRestaurant(RestaurantRequest request) {
        log.info("Creating restaurant: {}", request.getName());

        // Default 24h window so admin can activate without a second hours edit.
        var open = request.getOpenTime() != null ? request.getOpenTime() : java.time.LocalTime.MIDNIGHT;
        var close = request.getCloseTime() != null ? request.getCloseTime() : java.time.LocalTime.MIDNIGHT;

        Restaurant restaurant = Restaurant.builder()
                .ownerId(request.getOwnerId())
                .name(request.getName())
                .description(request.getDescription())
                .phone(request.getPhone())
                .addressLine(request.getAddressLine())
                .district(request.getDistrict())
                .city(request.getCity())
                .openTime(open)
                .closeTime(close)
                .minOrderAmount(request.getMinOrderAmount())
                .estimatedDeliveryTimeMin(request.getEstimatedDeliveryTimeMin())
                .logoUrl(request.getLogoUrl())
                .bannerUrl(request.getBannerUrl())
                .build();

        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Restaurant created with ID: {} (status={})", saved.getId(), saved.getStatus());

        return mapToResponse(saved);
    }

    @Override
    public RestaurantResponse updateRestaurant(UUID id, RestaurantRequest request) {
        log.info("Updating restaurant: {}", id);

        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found with ID: " + id));

        restaurant.setName(request.getName());
        restaurant.setDescription(request.getDescription());
        restaurant.setPhone(request.getPhone());
        restaurant.setAddressLine(request.getAddressLine());
        restaurant.setDistrict(request.getDistrict());
        restaurant.setCity(request.getCity());
        restaurant.setOpenTime(request.getOpenTime());
        restaurant.setCloseTime(request.getCloseTime());
        restaurant.setMinOrderAmount(request.getMinOrderAmount());
        restaurant.setEstimatedDeliveryTimeMin(request.getEstimatedDeliveryTimeMin());
        restaurant.setLogoUrl(request.getLogoUrl());
        restaurant.setBannerUrl(request.getBannerUrl());

        Restaurant updated = restaurantRepository.save(restaurant);

        return mapToResponse(updated);
    }

    @Override
    public void deleteRestaurant(UUID id) {
        log.info("Deleting restaurant: {}", id);
        if (!restaurantRepository.existsById(id)) {
            throw new RestaurantNotFoundException("Restaurant not found with ID: " + id);
        }
        restaurantRepository.deleteById(id);

    }

    @Override
    public RestaurantResponse getRestaurantById(UUID id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found with ID: " + id));
        return mapToResponse(restaurant);
    }

    @Override
    public List<RestaurantResponse> getAllRestaurants() {
        return restaurantRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantResponse> getRestaurantsByOwnerId(UUID ownerId) {
        return restaurantRepository.findByOwnerId(ownerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<RestaurantResponse> searchRestaurants(RestaurantSearchRequest request) {
        log.info("Searching restaurants with criteria: {}", request);

        Pageable pageable = PageRequest.of(
                request.getPage() != null ? request.getPage() : 0,
                request.getSize() != null ? request.getSize() : 10
        );

        RestaurantStatus status = null;
        if (request.getStatus() != null) {
            try {
                status = RestaurantStatus.valueOf(request.getStatus().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // Invalid status, ignore
            }
        }

        Page<Restaurant> page = restaurantRepository.searchRestaurants(
                request.getName(),
                request.getCity(),
                request.getDistrict(),
                status,
                request.getMinRating(),
                pageable
        );

        return page.map(this::mapToResponse);
    }

    @Override
    public RestaurantResponse setAvailability(UUID id, boolean accepting) {
        Restaurant restaurant = findRestaurant(id);
        restaurant.setAcceptingOrders(accepting);
        return mapToResponse(restaurantRepository.save(restaurant));
    }

    @Override
    public RestaurantResponse changeStatus(UUID id, RestaurantStatus status) {
        Restaurant restaurant = findRestaurant(id);
        restaurant.changeStatus(status);
        return mapToResponse(restaurantRepository.save(restaurant));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwner(UUID restaurantId, UUID userId) {
        return restaurantRepository.findById(restaurantId)
                .map(restaurant -> java.util.Objects.equals(restaurant.getOwnerId(), userId))
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found with ID: " + restaurantId));
    }

    private Restaurant findRestaurant(UUID id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found with ID: " + id));
    }

    private RestaurantResponse mapToResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .ownerId(restaurant.getOwnerId())
                .name(restaurant.getName())
                .description(restaurant.getDescription())
                .phone(restaurant.getPhone())
                .addressLine(restaurant.getAddressLine())
                .district(restaurant.getDistrict())
                .city(restaurant.getCity())
                .status(restaurant.getStatus() != null ? restaurant.getStatus().name() : "PENDING")
                .openTime(restaurant.getOpenTime())
                .closeTime(restaurant.getCloseTime())
                .avgRating(restaurant.getAvgRating())
                .totalReviews(restaurant.getTotalReviews())
                .minOrderAmount(restaurant.getMinOrderAmount())
                .estimatedDeliveryTimeMin(restaurant.getEstimatedDeliveryTimeMin())
                .logoUrl(restaurant.getLogoUrl())
                .bannerUrl(restaurant.getBannerUrl())
                .isAcceptingOrders(restaurant.getIsAcceptingOrders())
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }
}
