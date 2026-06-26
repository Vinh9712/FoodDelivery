package com.fooddelivery.notification.api.controller;

import com.fooddelivery.notification.application.NotificationStore;
import com.fooddelivery.notification.domain.NotificationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/**
 * REST API for viewing recent notification logs.
 * Useful for testing end-to-end event flow.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationStore store;

    /**
     * Returns up to the last 200 notification log entries, newest first.
     */
    @GetMapping
    public Collection<NotificationLog> getNotifications() {
        return store.getAll();
    }
}
