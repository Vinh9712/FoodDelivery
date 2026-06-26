package com.fooddelivery.restaurant.domain.model;

import com.fooddelivery.restaurant.domain.exception.ReviewAlreadyRepliedException;
import com.fooddelivery.restaurant.domain.model.valueobject.Rating;
import com.fooddelivery.restaurant.domain.model.valueobject.ReviewReply;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.restaurant.domain.util.UuidCreator;

@Entity
@Table(name = "restaurant_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantReview {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "comment")
    private String comment;

    @Column(name = "reply_text")
    private String replyText;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RestaurantReview(UUID id, UUID restaurantId, UUID customerId, UUID orderId, Rating rating, String comment) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.customerId = customerId;
        this.orderId = orderId;
        this.rating = rating.value();
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public static RestaurantReview submit(UUID restaurantId, UUID customerId, UUID orderId, Rating rating, String comment) {
        return new RestaurantReview(UuidCreator.nextUuidV7(), restaurantId, customerId, orderId, rating, comment);
    }

    public Rating getRating() {
        return new Rating(this.rating);
    }

    public ReviewReply getOwnerReply() {
        if (this.replyText == null || this.repliedAt == null) {
            return null;
        }
        return new ReviewReply(this.replyText, this.repliedAt);
    }

    public void addOwnerReply(String replyText) {
        if (this.replyText != null || this.repliedAt != null) {
            throw new ReviewAlreadyRepliedException(this.id);
        }
        ReviewReply reply = new ReviewReply(replyText, Instant.now());
        this.replyText = reply.text();
        this.repliedAt = reply.repliedAt();
    }
}
