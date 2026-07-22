package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.api.dto.DeliveryRequest;
import com.fooddelivery.delivery.api.dto.DeliveryResponse;
import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import com.fooddelivery.delivery.domain.model.Delivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/deliveries")
@RequiredArgsConstructor
@Slf4j
public class InternalDeliveryController {

    private final DeliveryAssignmentService deliveryAssignmentService;

    @PostMapping
    public ResponseEntity<DeliveryResponse> scheduleDelivery(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody DeliveryRequest request) {
        if (request == null || request.orderId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
        }
        String expectedKey = "delivery-schedule:" + request.orderId();
        if (!StringUtils.hasText(idempotencyKey) || !expectedKey.equals(idempotencyKey)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Idempotency-Key must equal delivery-schedule:{orderId}");
        }
        if (request.pickupAddressSnapshot() == null
                || !StringUtils.hasText(request.pickupAddressSnapshot().addressText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pickupAddressSnapshot is required");
        }
        if (request.dropoffAddressSnapshot() == null
                || !StringUtils.hasText(request.dropoffAddressSnapshot().addressLine())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dropoffAddressSnapshot is required");
        }

        DeliveryAssignmentService.AssignmentResult result =
                deliveryAssignmentService.scheduleDelivery(idempotencyKey, request);

        String responseStatus = result.assigned() ? "ASSIGNED" : result.deliveryStatus().name();
        log.info("Delivery scheduling completed: orderId={}, deliveryId={}, status={}, driverId={}",
                result.orderId(), result.deliveryId(), responseStatus, result.driverId());

        return ResponseEntity.ok(new DeliveryResponse(
                result.deliveryId(),
                result.orderId(),
                responseStatus,
                result.driverId(),
                result.message()));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<DeliveryResponse> findByOrderId(@PathVariable UUID orderId) {
        Delivery delivery = deliveryAssignmentService.getByOrderId(orderId);
        String status = delivery.getDriverId() != null && delivery.getStatus().name().equals("DRIVER_ASSIGNED")
                ? "ASSIGNED"
                : delivery.getStatus().name();
        return ResponseEntity.ok(new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                status,
                delivery.getDriverId(),
                null));
    }
}
