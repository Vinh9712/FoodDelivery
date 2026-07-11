package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.api.dto.DeliveryDetailResponse;
import com.fooddelivery.delivery.api.dto.FailDeliveryRequest;
import com.fooddelivery.delivery.api.dto.TrackingPointResponse;
import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import com.fooddelivery.delivery.application.service.DeliveryLifecycleService;
import com.fooddelivery.delivery.domain.model.Delivery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for delivery lifecycle and admin assignment.
 */
@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryAssignmentService deliveryAssignmentService;
    private final DeliveryLifecycleService deliveryLifecycleService;

    @PostMapping("/{deliveryId}/assign-driver/{driverId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignDriver(
            @PathVariable UUID deliveryId,
            @PathVariable UUID driverId) {
        deliveryAssignmentService.assignDriver(deliveryId, driverId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DeliveryDetailResponse> accept(
            @PathVariable("id") UUID id,
            Authentication authentication) {
        Delivery delivery = deliveryLifecycleService.accept(id, subject(authentication));
        return ResponseEntity.ok(DeliveryDetailResponse.from(delivery));
    }

    @PostMapping("/{id}/picked-up")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DeliveryDetailResponse> pickedUp(
            @PathVariable("id") UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(DeliveryDetailResponse.from(
                deliveryLifecycleService.pickUp(id, subject(authentication))));
    }

    @PostMapping("/{id}/start-delivery")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DeliveryDetailResponse> startDelivery(
            @PathVariable("id") UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(DeliveryDetailResponse.from(
                deliveryLifecycleService.startDelivery(id, subject(authentication))));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DeliveryDetailResponse> complete(
            @PathVariable("id") UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(DeliveryDetailResponse.from(
                deliveryLifecycleService.complete(id, subject(authentication))));
    }

    @PostMapping("/{id}/fail")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DeliveryDetailResponse> fail(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) FailDeliveryRequest request,
            Authentication authentication) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(DeliveryDetailResponse.from(
                deliveryLifecycleService.fail(id, subject(authentication), reason)));
    }

    @GetMapping("/{id}/tracking")
    @PreAuthorize("@deliveryAuthorization.canRead(#id, authentication)")
    public ResponseEntity<List<TrackingPointResponse>> tracking(@PathVariable("id") UUID id) {
        List<TrackingPointResponse> points = deliveryLifecycleService.getTracking(id).stream()
                .map(TrackingPointResponse::from)
                .toList();
        return ResponseEntity.ok(points);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@deliveryAuthorization.canRead(#id, authentication)")
    public ResponseEntity<DeliveryDetailResponse> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(DeliveryDetailResponse.from(deliveryLifecycleService.getDelivery(id)));
    }

    private UUID subject(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
