package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.ReviewRequest;
import com.fooddelivery.restaurant.api.dto.ReviewResponse;
import com.fooddelivery.restaurant.application.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/restaurants/{restaurantId}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable("restaurantId") UUID restaurantId,
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication) {
        log.info("POST /restaurants/{}/reviews - Create review", restaurantId);
        request.setCustomerId(UUID.fromString(authentication.getName()));
        ReviewResponse response = reviewService.createReview(restaurantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/restaurants/{restaurantId}/reviews")
    public ResponseEntity<Page<ReviewResponse>> getReviewsByRestaurant(
            @PathVariable("restaurantId") UUID restaurantId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("GET /restaurants/{}/reviews - Get reviews", restaurantId);
        Pageable pageable = PageRequest.of(page, size);
        Page<ReviewResponse> responses = reviewService.getReviewsByRestaurant(restaurantId, pageable);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<ReviewResponse> getReviewById(
            @PathVariable("reviewId") UUID reviewId) {
        log.info("GET /reviews/{} - Get review", reviewId);
        ReviewResponse response = reviewService.getReviewById(reviewId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/restaurants/{restaurantId}/rating")
    public ResponseEntity<Double> getAverageRating(
            @PathVariable("restaurantId") UUID restaurantId) {
        log.info("GET /restaurants/{}/rating - Get average rating", restaurantId);
        Double rating = reviewService.getAverageRating(restaurantId);
        return ResponseEntity.ok(rating != null ? rating : 0.0);
    }

}
