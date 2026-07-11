package com.fooddelivery.order.infrastructure.client;

import com.fooddelivery.order.infrastructure.client.dto.DeliveryRequest;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative HTTP client cho Delivery Service.
 * <p>
 * Sử dụng Spring Cloud OpenFeign kết hợp Eureka Service Discovery.
 * </p>
 */
@FeignClient(name = "delivery-service")
public interface DeliveryServiceClient {

    /**
     * Gọi API lập lịch giao vận.
     *
     * @param request chứa orderId, deliveryAddressSnapshot
     * @return kết quả phân bổ tài xế (ASSIGNED hoặc FAILED)
     */
    @PostMapping("/internal/v1/deliveries")
    DeliveryResponse scheduleDelivery(@RequestBody DeliveryRequest request);
}
