package com.fooddelivery.notification.api.controller;

import com.fooddelivery.notification.api.dto.CustomerNotificationDto;
import com.fooddelivery.notification.application.CustomerNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Customer in-app notification inbox.
 */
@RestController
@RequestMapping("/api/v1/notifications/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CustomerNotificationController {

    private final CustomerNotificationService customerNotificationService;

    @GetMapping
    public ResponseEntity<Page<CustomerNotificationDto>> list(
            @RequestParam(required = false) Boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(customerNotificationService.listMine(userId, unreadOnly, pageable));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<CustomerNotificationDto> markRead(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(customerNotificationService.markRead(userId, id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        int updated = customerNotificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
