package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.internal.MenuQuoteRequest;
import com.fooddelivery.restaurant.api.dto.internal.MenuQuoteResponse;
import com.fooddelivery.restaurant.application.MenuQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/restaurants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE')")
public class InternalMenuQuoteController {

    private final MenuQuoteService menuQuoteService;

    @PostMapping("/{restaurantId}/menu/quote")
    public MenuQuoteResponse quote(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody MenuQuoteRequest request) {
        return menuQuoteService.quote(restaurantId, request);
    }
}
