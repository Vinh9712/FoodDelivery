package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.internal.ReviewEligibilityResponse;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE')")
public class InternalOrderController {

    private final OrderRepository orderRepository;

    @GetMapping("/{orderId}/review-eligibility")
    public ReviewEligibilityResponse reviewEligibility(
            @PathVariable UUID orderId,
            @RequestParam UUID customerId,
            @RequestParam UUID restaurantId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (!customerId.equals(order.getCustomerId())) {
                        return new ReviewEligibilityResponse(orderId, false, "Order does not belong to customer");
                    }
                    if (!restaurantId.equals(order.getRestaurantId())) {
                        return new ReviewEligibilityResponse(orderId, false, "Order does not belong to restaurant");
                    }
                    if (order.getStatus() != OrderStatus.DELIVERED) {
                        return new ReviewEligibilityResponse(orderId, false, "Only delivered orders can be reviewed");
                    }
                    return new ReviewEligibilityResponse(orderId, true, "Eligible verified purchase");
                })
                .orElseGet(() -> new ReviewEligibilityResponse(orderId, false, "Order not found"));
    }
}
