package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Delivery operations.
 */
@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryAssignmentService deliveryAssignmentService;

    /**
     * Manually assign a driver to a delivery (admin/support use).
     */
    @PostMapping("/{deliveryId}/assign-driver/{driverId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignDriver(
            @PathVariable UUID deliveryId,
            @PathVariable UUID driverId) {
        deliveryAssignmentService.assignDriver(deliveryId, driverId);
        return ResponseEntity.ok().build();
    }
}
