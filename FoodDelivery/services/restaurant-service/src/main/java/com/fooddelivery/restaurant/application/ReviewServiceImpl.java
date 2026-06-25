package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.ReviewRequest;
import com.fooddelivery.restaurant.api.dto.ReviewResponse;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import com.fooddelivery.restaurant.domain.RestaurantReview;
import com.fooddelivery.restaurant.domain.ReviewRepository;
import com.fooddelivery.restaurant.exception.RestaurantNotFoundException;
import com.fooddelivery.restaurant.exception.ReviewAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final RestaurantRepository restaurantRepository;

    @Override
    public ReviewResponse createReview(UUID restaurantId, ReviewRequest request) {
        log.info("Creating review for restaurant: {}", restaurantId);

        // Kiểm tra restaurant tồn tại
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + restaurantId));

        // Kiểm tra đã review chưa (1 order chỉ review 1 lần)
        if (reviewRepository.existsByRestaurantIdAndOrderId(restaurantId, request.getOrderId())) {
            throw new ReviewAlreadyExistsException("This order has already been reviewed");
        }

        RestaurantReview review = RestaurantReview.builder()
                .restaurant(restaurant)
                .customerId(request.getCustomerId())
                .orderId(request.getOrderId())
                .rating(request.getRating())
                .comment(request.getComment())
                .isVerifiedPurchase(true)
                .build();

        RestaurantReview saved = reviewRepository.save(review);

        // Cập nhật rating cho restaurant
        updateRestaurantRating(restaurantId);

        return mapToResponse(saved);
    }

    @Override
    public Page<ReviewResponse> getReviewsByRestaurant(UUID restaurantId, Pageable pageable) {
        log.info("Getting reviews for restaurant: {}", restaurantId);
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new RestaurantNotFoundException("Restaurant not found: " + restaurantId);
        }
        return reviewRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public Page<ReviewResponse> getReviewsByCustomer(UUID customerId, Pageable pageable) {
        log.info("Getting reviews by customer: {}", customerId);
        return reviewRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public ReviewResponse getReviewById(UUID reviewId) {
        RestaurantReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found: " + reviewId));
        return mapToResponse(review);
    }

    @Override
    public Double getAverageRating(UUID restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new RestaurantNotFoundException("Restaurant not found: " + restaurantId);
        }
        return reviewRepository.calculateAverageRating(restaurantId);
    }

    private void updateRestaurantRating(UUID restaurantId) {
        Double avgRating = reviewRepository.calculateAverageRating(restaurantId);
        Long totalReviews = reviewRepository.countReviewsByRestaurantId(restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + restaurantId));

        restaurant.setAvgRating(avgRating != null ? BigDecimal.valueOf(avgRating) : BigDecimal.ZERO);
        restaurant.setTotalReviews(totalReviews != null ? totalReviews.intValue() : 0);

        restaurantRepository.save(restaurant);
        log.info("Updated rating for restaurant {}: avg={}, total={}", restaurantId, avgRating, totalReviews);
    }

    private ReviewResponse mapToResponse(RestaurantReview review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .restaurantId(review.getRestaurant().getId())
                .restaurantName(review.getRestaurant().getName())
                .customerId(review.getCustomerId())
                .orderId(review.getOrderId())
                .rating(review.getRating())
                .comment(review.getComment())
                .isVerifiedPurchase(review.getIsVerifiedPurchase())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}