package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.RestaurantRequest;
import com.fooddelivery.restaurant.api.dto.RestaurantResponse;
import com.fooddelivery.restaurant.api.dto.RestaurantSearchRequest;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import com.fooddelivery.restaurant.domain.RestaurantStatus;
import com.fooddelivery.restaurant.event.EventPublisher;
import com.fooddelivery.restaurant.exception.RestaurantNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceImplTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private RestaurantServiceImpl restaurantService;

    private UUID restaurantId;
    private UUID ownerId;
    private RestaurantRequest request;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurantId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        request = RestaurantRequest.builder()
                .ownerId(ownerId)
                .name("Test Restaurant")
                .description("Test Description")
                .phone("0123456789")
                .addressLine("123 Test St")
                .district("District 1")
                .city("Ho Chi Minh")
                .build();

        restaurant = Restaurant.builder()
                .id(restaurantId)
                .ownerId(ownerId)
                .name("Test Restaurant")
                .description("Test Description")
                .phone("0123456789")
                .addressLine("123 Test St")
                .district("District 1")
                .city("Ho Chi Minh")
                .status(RestaurantStatus.PENDING)
                .avgRating(BigDecimal.ZERO)
                .totalReviews(0)
                .minOrderAmount(BigDecimal.ZERO)
                .estimatedDeliveryTimeMin(30)
                .isAcceptingOrders(true)
                .build();
    }

    @Test
    void createRestaurant_ShouldReturnRestaurantResponse_WhenSuccess() {
        // Arrange
        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(restaurant);

        // Act
        RestaurantResponse response = restaurantService.createRestaurant(request);

        // Assert
        assertNotNull(response);
        assertEquals(restaurantId, response.getId());
        assertEquals(request.getName(), response.getName());
        assertEquals(request.getCity(), response.getCity());
        assertEquals("PENDING", response.getStatus());

        verify(restaurantRepository, times(1)).save(any(Restaurant.class));
        verify(eventPublisher, times(1)).publishRestaurantCreated(any(Restaurant.class));
    }

    @Test
    void getRestaurantById_ShouldReturnRestaurantResponse_WhenRestaurantExists() {
        // Arrange
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        // Act
        RestaurantResponse response = restaurantService.getRestaurantById(restaurantId);

        // Assert
        assertNotNull(response);
        assertEquals(restaurantId, response.getId());
        assertEquals(restaurant.getName(), response.getName());
        assertEquals(restaurant.getCity(), response.getCity());

        verify(restaurantRepository, times(1)).findById(restaurantId);
    }

    @Test
    void getRestaurantById_ShouldThrowRestaurantNotFoundException_WhenRestaurantNotExists() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(restaurantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RestaurantNotFoundException.class, () -> {
            restaurantService.getRestaurantById(nonExistentId);
        });

        verify(restaurantRepository, times(1)).findById(nonExistentId);
    }

    @Test
    void getAllRestaurants_ShouldReturnListOfRestaurantResponses() {
        // Arrange
        Restaurant restaurant2 = Restaurant.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .name("Restaurant 2")
                .city("Da Nang")
                .status(RestaurantStatus.ACTIVE)
                .avgRating(BigDecimal.valueOf(4.5))
                .totalReviews(10)
                .minOrderAmount(BigDecimal.valueOf(50000))
                .estimatedDeliveryTimeMin(30)
                .isAcceptingOrders(true)
                .build();

        when(restaurantRepository.findAll()).thenReturn(Arrays.asList(restaurant, restaurant2));

        // Act
        List<RestaurantResponse> responses = restaurantService.getAllRestaurants();

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("Test Restaurant", responses.get(0).getName());
        assertEquals("Restaurant 2", responses.get(1).getName());

        verify(restaurantRepository, times(1)).findAll();
    }

    @Test
    void updateRestaurant_ShouldReturnUpdatedRestaurantResponse_WhenSuccess() {
        // Arrange
        RestaurantRequest updateRequest = RestaurantRequest.builder()
                .ownerId(ownerId)
                .name("Updated Restaurant")
                .description("Updated Description")
                .phone("0987654321")
                .addressLine("456 Updated St")
                .district("District 2")
                .city("Da Nang")
                .build();

        Restaurant updatedRestaurant = Restaurant.builder()
                .id(restaurantId)
                .ownerId(ownerId)
                .name("Updated Restaurant")
                .description("Updated Description")
                .phone("0987654321")
                .addressLine("456 Updated St")
                .district("District 2")
                .city("Da Nang")
                .status(RestaurantStatus.PENDING)
                .avgRating(BigDecimal.ZERO)
                .totalReviews(0)
                .minOrderAmount(BigDecimal.ZERO)
                .estimatedDeliveryTimeMin(30)
                .isAcceptingOrders(true)
                .build();

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenReturn(updatedRestaurant);

        // Act
        RestaurantResponse response = restaurantService.updateRestaurant(restaurantId, updateRequest);

        // Assert
        assertNotNull(response);
        assertEquals(restaurantId, response.getId());
        assertEquals("Updated Restaurant", response.getName());
        assertEquals("Da Nang", response.getCity());

        verify(restaurantRepository, times(1)).findById(restaurantId);
        verify(restaurantRepository, times(1)).save(any(Restaurant.class));
        verify(eventPublisher, times(1)).publishRestaurantUpdated(any(Restaurant.class));
    }

    @Test
    void deleteRestaurant_ShouldDeleteRestaurant_WhenRestaurantExists() {
        // Arrange
        when(restaurantRepository.existsById(restaurantId)).thenReturn(true);
        doNothing().when(restaurantRepository).deleteById(restaurantId);

        // Act
        restaurantService.deleteRestaurant(restaurantId);

        // Assert
        verify(restaurantRepository, times(1)).existsById(restaurantId);
        verify(restaurantRepository, times(1)).deleteById(restaurantId);
        verify(eventPublisher, times(1)).publishRestaurantDeleted(restaurantId);
    }

    @Test
    void deleteRestaurant_ShouldThrowRestaurantNotFoundException_WhenRestaurantNotExists() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(restaurantRepository.existsById(nonExistentId)).thenReturn(false);

        // Act & Assert
        assertThrows(RestaurantNotFoundException.class, () -> {
            restaurantService.deleteRestaurant(nonExistentId);
        });

        verify(restaurantRepository, times(1)).existsById(nonExistentId);
        verify(restaurantRepository, never()).deleteById(any());
        verify(eventPublisher, never()).publishRestaurantDeleted(any());
    }

    @Test
    void searchRestaurants_ShouldReturnPageOfRestaurantResponses() {
        // Arrange
        RestaurantSearchRequest searchRequest = RestaurantSearchRequest.builder()
                .name("Test")
                .city("Ho Chi Minh")
                .page(0)
                .size(10)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Restaurant> page = new PageImpl<>(Arrays.asList(restaurant), pageable, 1);

        when(restaurantRepository.searchRestaurants(
                eq("Test"),
                eq("Ho Chi Minh"),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)))
                .thenReturn(page);

        // Act
        Page<RestaurantResponse> responses = restaurantService.searchRestaurants(searchRequest);

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.getTotalElements());
        assertEquals("Test Restaurant", responses.getContent().get(0).getName());

        verify(restaurantRepository, times(1)).searchRestaurants(
                eq("Test"),
                eq("Ho Chi Minh"),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class));
    }
}