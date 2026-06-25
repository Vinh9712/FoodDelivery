package com.fooddelivery.notification.api.controller;

import com.fooddelivery.notification.api.dto.NotificationRequest;
import com.fooddelivery.notification.api.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Notification Service MVP Controller.
 * <p>
 * Tiếp nhận thông điệp, lưu trữ cục bộ (in-memory) và
 * sử dụng SLF4J Logger để giả lập gửi email/in-app thành công.
 * </p>
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationMvpController {

    private static final Logger log = LoggerFactory.getLogger(NotificationMvpController.class);

    /** Kho lưu trữ thông báo cục bộ (in-memory, giả lập persistence) */
    private final Map<UUID, NotificationRequest> notificationStore = new ConcurrentHashMap<>();

    /**
     * Gửi thông báo.
     *
     * @param request chứa orderId, customerId, channel, subject, message
     * @return NotificationResponse xác nhận đã gửi thành công
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody NotificationRequest request) {
        var notificationId = UUID.randomUUID();

        // Lưu trữ cục bộ
        notificationStore.put(notificationId, request);

        // Giả lập gửi thông báo qua console log
        log.info("""
                ══════════════════════════════════════════════════════
                📧 THÔNG BÁO ĐÃ GỬI THÀNH CÔNG
                ──────────────────────────────────────────────────────
                 Notification ID : {}
                 Order ID        : {}
                 Customer ID     : {}
                 Kênh            : {}
                 Tiêu đề         : {}
                 Nội dung        : {}
                 Thời gian       : {}
                ══════════════════════════════════════════════════════""",
                notificationId,
                request.orderId(),
                request.customerId(),
                request.channel() != null ? request.channel() : "IN_APP",
                request.subject(),
                request.message(),
                Instant.now()
        );

        var response = new NotificationResponse(
                notificationId,
                "SENT",
                Instant.now(),
                "Thông báo đã được gửi thành công"
        );
        return ResponseEntity.ok(response);
    }
}
