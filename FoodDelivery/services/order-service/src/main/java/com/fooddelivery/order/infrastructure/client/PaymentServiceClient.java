package com.fooddelivery.order.infrastructure.client;

import com.fooddelivery.order.infrastructure.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative HTTP client cho Payment Service.
 * <p>
 * Sử dụng Spring Cloud OpenFeign kết hợp Eureka Service Discovery.
 * Tên {@code "payment-service"} phải khớp với {@code spring.application.name}
 * của Payment Service đã đăng ký trên Eureka.
 * </p>
 */
@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    /**
     * Gọi API thanh toán đơn hàng.
     *
     * @param request chứa orderId, customerId, amount
     * @return kết quả thanh toán (SUCCESS hoặc FAILED)
     */
    @PostMapping("/api/payments")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);

    /**
     * Gọi API hoàn tiền (compensating transaction).
     *
     * @param request chứa orderId, amount
     * @return kết quả hoàn tiền (REFUNDED)
     */
    @PostMapping("/api/payments/refund")
    RefundResponse refundPayment(@RequestBody RefundRequest request);
}
