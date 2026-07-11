package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.api.dto.LocationUpdateRequest;
import com.fooddelivery.delivery.application.service.DriverSelfService;
import com.fooddelivery.delivery.domain.model.Driver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers/me")
@RequiredArgsConstructor
public class DriverController {

    private final DriverSelfService driverSelfService;

    @PostMapping("/online")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Map<String, Object>> goOnline(Authentication authentication) {
        Driver driver = driverSelfService.goOnline(subject(authentication));
        return ResponseEntity.ok(Map.of(
                "driverId", driver.getId(),
                "online", driver.isOnline(),
                "available", driver.isAvailable()));
    }

    @PostMapping("/offline")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Map<String, Object>> goOffline(Authentication authentication) {
        Driver driver = driverSelfService.goOffline(subject(authentication));
        return ResponseEntity.ok(Map.of(
                "driverId", driver.getId(),
                "online", driver.isOnline(),
                "available", driver.isAvailable()));
    }

    @PutMapping("/location")
    @PreAuthorize("hasRole('DRIVER')")
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
