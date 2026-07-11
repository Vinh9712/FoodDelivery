package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.api.dto.DeliveryRequest;
import com.fooddelivery.delivery.api.dto.DeliveryResponse;
import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/deliveries")
@RequiredArgsConstructor
@Slf4j
public class InternalDeliveryController {

    private final DeliveryAssignmentService deliveryAssignmentService;

    @PostMapping
    public ResponseEntity<DeliveryResponse> scheduleDelivery(@RequestBody DeliveryRequest request) {
        if (request.orderId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
        }
        if (request.deliveryAddressSnapshot() == null || request.deliveryAddressSnapshot().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliveryAddressSnapshot is required");
        }

        DeliveryAssignmentService.AssignmentResult result = deliveryAssignmentService.scheduleDelivery(
                request.orderId(), request.customerId(), request.deliveryAddressSnapshot());

        String responseStatus = result.assigned() ? "ASSIGNED" : "FAILED";
        log.info("Delivery scheduling completed: orderId={}, deliveryId={}, status={}, driverId={}",
                result.orderId(), result.deliveryId(), responseStatus, result.driverId());

        return ResponseEntity.ok(new DeliveryResponse(
                result.orderId(), responseStatus, result.driverId(), result.message()));
    }
}
