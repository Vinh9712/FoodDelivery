package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.ReviewRequest;
import com.fooddelivery.restaurant.api.dto.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {
    ReviewResponse createReview(UUID restaurantId, ReviewRequest request);
    Page<ReviewResponse> getReviewsByRestaurant(UUID restaurantId, Pageable pageable);
    Page<ReviewResponse> getReviewsByCustomer(UUID customerId, Pageable pageable);
    ReviewResponse getReviewById(UUID reviewId);
    Double getAverageRating(UUID restaurantId);
}