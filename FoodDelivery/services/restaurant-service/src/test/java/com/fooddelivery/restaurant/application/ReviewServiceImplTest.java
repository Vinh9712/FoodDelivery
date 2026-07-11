package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.ReviewRequest;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import com.fooddelivery.restaurant.domain.RestaurantReview;
import com.fooddelivery.restaurant.domain.ReviewRepository;
import com.fooddelivery.restaurant.exception.ReviewVerificationException;
import com.fooddelivery.restaurant.infrastructure.client.OrderServiceClient;
import com.fooddelivery.restaurant.infrastructure.client.dto.ReviewEligibilityResponse;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    private ReviewServiceImpl reviewService;
    private UUID restaurantId;
    private UUID customerId;
    private UUID orderId;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewRepository, restaurantRepository, orderServiceClient);
        restaurantId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        restaurant = Restaurant.builder()
                .id(restaurantId)
                .name("Verified Restaurant")
                .avgRating(BigDecimal.ZERO)
                .totalReviews(0)
                .build();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    }

    @Test
    void createReviewRequiresDeliveredOwnedOrderAndMarksVerifiedPurchase() {
        ReviewRequest request = request();
        when(orderServiceClient.getReviewEligibility(orderId, customerId, restaurantId))
                .thenReturn(new ReviewEligibilityResponse(orderId, true, "eligible"));
        when(reviewRepository.existsByOrderId(orderId)).thenReturn(false);
        when(reviewRepository.save(any(RestaurantReview.class))).thenAnswer(invocation -> {
            RestaurantReview review = invocation.getArgument(0);
            review.setId(UUID.randomUUID());
            return review;
        });
        when(reviewRepository.calculateAverageRating(restaurantId)).thenReturn(5.0);
        when(reviewRepository.countReviewsByRestaurantId(restaurantId)).thenReturn(1L);

        var response = reviewService.createReview(restaurantId, request);

        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getIsVerifiedPurchase()).isTrue();
    }

    @Test
    void createReviewRejectsOrderThatDoesNotBelongToCustomerOrRestaurant() {
        when(orderServiceClient.getReviewEligibility(orderId, customerId, restaurantId))
                .thenReturn(new ReviewEligibilityResponse(orderId, false, "Order does not belong to customer"));

        assertThatThrownBy(() -> reviewService.createReview(restaurantId, request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReviewFailsClosedWhenOrderServiceIsUnavailable() {
        FeignException failure = mock(FeignException.class);
        when(orderServiceClient.getReviewEligibility(orderId, customerId, restaurantId)).thenThrow(failure);

        assertThatThrownBy(() -> reviewService.createReview(restaurantId, request()))
                .isInstanceOf(ReviewVerificationException.class);
        verify(reviewRepository, never()).save(any());
    }

    private ReviewRequest request() {
        return ReviewRequest.builder()
                .customerId(customerId)
                .orderId(orderId)
                .rating(5)
                .comment("Delivered and verified")
                .build();
    }
}
