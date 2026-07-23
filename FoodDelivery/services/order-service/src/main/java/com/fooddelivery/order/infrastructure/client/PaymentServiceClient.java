package com.fooddelivery.order.infrastructure.client;

import com.fooddelivery.order.infrastructure.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

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
    @PostMapping("/internal/v1/payments")
    PaymentResponse processPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request);

    @GetMapping("/internal/v1/payments/orders/{orderId}")
    PaymentResponse getPaymentByOrderId(@PathVariable("orderId") UUID orderId);

    /**
     * Gọi API hoàn tiền (compensating transaction).
     *
     * @param request chứa orderId, amount
     * @return kết quả hoàn tiền (REFUNDED)
     */
    @PostMapping("/internal/v1/payments/refund")
    RefundResponse refundPayment(@RequestBody RefundRequest request);
}
