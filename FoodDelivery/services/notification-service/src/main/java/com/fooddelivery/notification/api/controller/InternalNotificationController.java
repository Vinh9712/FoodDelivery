package com.fooddelivery.notification.api.controller;

import com.fooddelivery.notification.api.dto.NotificationRequest;
import com.fooddelivery.notification.api.dto.NotificationResponse;
import com.fooddelivery.notification.application.NotificationJobService;
import com.fooddelivery.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationJobService notificationJobService;

    @PostMapping
    public ResponseEntity<NotificationResponse> enqueue(@RequestBody NotificationRequest request) {
        validate(request);
        try {
            Notification notification = notificationJobService.enqueue(request);
            return ResponseEntity.accepted().body(new NotificationResponse(
                    notification.getId(), notification.getStatus().name(), notification.getSentAt(),
                    "Notification accepted for asynchronous delivery"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private void validate(NotificationRequest request) {
        if (request.orderId() == null || request.customerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId and customerId are required");
        }
        if (request.subject() == null || request.subject().isBlank()
                || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject and message are required");
        }
    }
}
