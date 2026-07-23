package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.api.dto.DeliveryDetailResponse;
import com.fooddelivery.delivery.api.dto.DriverProfileResponse;
import com.fooddelivery.delivery.api.dto.LocationUpdateRequest;
import com.fooddelivery.delivery.api.dto.UpdateDriverProfileRequest;
import com.fooddelivery.delivery.application.service.DriverSelfService;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class DriverController {

    private final DriverSelfService driverSelfService;

    @GetMapping
    public ResponseEntity<DriverProfileResponse> getProfile(Authentication authentication) {
        Driver driver = driverSelfService.getProfile(subject(authentication));
        return ResponseEntity.ok(DriverProfileResponse.from(driver));
    }

    @PutMapping
    public ResponseEntity<DriverProfileResponse> upsertProfile(
            @Valid @RequestBody UpdateDriverProfileRequest request,
            Authentication authentication) {
        Driver driver = driverSelfService.upsertProfile(subject(authentication), request);
        return ResponseEntity.ok(DriverProfileResponse.from(driver));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<Page<DeliveryDetailResponse>> listDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Page<Delivery> page = driverSelfService.listMyDeliveries(subject(authentication), status, pageable);
        return ResponseEntity.ok(page.map(DeliveryDetailResponse::from));
    }

    @GetMapping("/deliveries/current")
    public ResponseEntity<DeliveryDetailResponse> currentDelivery(Authentication authentication) {
        return driverSelfService.currentDelivery(subject(authentication))
                .map(d -> ResponseEntity.ok(DeliveryDetailResponse.from(d)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/online")
    public ResponseEntity<Map<String, Object>> goOnline(Authentication authentication) {
        Driver driver = driverSelfService.goOnline(subject(authentication));
        return ResponseEntity.ok(Map.of(
                "driverId", driver.getId(),
                "online", driver.isOnline(),
                "available", driver.isAvailable()));
    }

    @PostMapping("/offline")
    public ResponseEntity<Map<String, Object>> goOffline(Authentication authentication) {
        Driver driver = driverSelfService.goOffline(subject(authentication));
        return ResponseEntity.ok(Map.of(
                "driverId", driver.getId(),
                "online", driver.isOnline(),
                "available", driver.isAvailable()));
    }

    @PutMapping("/location")
    public ResponseEntity<Map<String, Object>> updateLocation(
            @RequestBody LocationUpdateRequest request,
            Authentication authentication) {
        Driver driver = driverSelfService.updateLocation(
                subject(authentication), request.latitude(), request.longitude());
        return ResponseEntity.ok(Map.of(
                "driverId", driver.getId(),
                "latitude", driver.getCurrentLatitude(),
                "longitude", driver.getCurrentLongitude(),
                "locationUpdatedAt", driver.getLocationUpdatedAt().toString()));
    }

    private UUID subject(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
