package com.fooddelivery.notification.api.controller;

import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.infrastructure.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for viewing recent persistent notification jobs.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    /**
     * Returns up to the last 200 notification jobs, newest first.
     */
    @GetMapping
    public List<Notification> getNotifications() {
        return notificationRepository.findTop200ByOrderByCreatedAtDesc();
    }
}
