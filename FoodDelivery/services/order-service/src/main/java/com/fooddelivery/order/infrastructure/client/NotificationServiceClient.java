package com.fooddelivery.order.infrastructure.client;

import com.fooddelivery.order.infrastructure.client.dto.NotificationRequest;
import com.fooddelivery.order.infrastructure.client.dto.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative HTTP client cho Notification Service.
 * <p>
 * Sử dụng Spring Cloud OpenFeign kết hợp Eureka Service Discovery.
 * </p>
 */
@FeignClient(name = "notification-service")
public interface NotificationServiceClient {

    /**
     * Gửi thông báo đến khách hàng.
     *
     * @param request chứa orderId, customerId, channel, subject, message
     * @return xác nhận gửi thành công
     */
    @PostMapping("/internal/v1/notifications")
    NotificationResponse sendNotification(@RequestBody NotificationRequest request);
}
