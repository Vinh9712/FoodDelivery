package com.fooddelivery.payment.api.controller;

import com.fooddelivery.payment.api.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment Service MVP Controller.
 * <p>
 * Triển khai tối giản để phục vụ kiểm thử luồng Saga:
 * <ul>
 *   <li>Nếu amount > 500,000 VND → FAILED ("Sự cố số dư tài khoản")</li>
 *   <li>Ngược lại → SUCCESS kèm transactionId</li>
 *   <li>Refund luôn trả về REFUNDED</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentMvpController {

    private static final Logger log = LoggerFactory.getLogger(PaymentMvpController.class);

    /** Ngưỡng số tiền tối đa cho phép thanh toán thành công (500,000 VND) */
    private static final BigDecimal MAX_ALLOWED_AMOUNT = new BigDecimal("500000");

    /**
     * Xử lý thanh toán đơn hàng.
     *
     * @param request chứa orderId, customerId, amount
     * @return PaymentResponse với status SUCCESS hoặc FAILED
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        log.info("📥 Nhận yêu cầu thanh toán: orderId={}, customerId={}, amount={}",
                request.orderId(), request.customerId(), request.amount());

        // Kiểm tra ngưỡng số tiền — giả lập logic nghiệp vụ
        if (request.amount() != null && request.amount().compareTo(MAX_ALLOWED_AMOUNT) > 0) {
            log.warn("❌ Thanh toán thất bại: amount {} vượt ngưỡng {} VND",
                    request.amount(), MAX_ALLOWED_AMOUNT);

            var response = new PaymentResponse(
                    request.orderId(),
                    "FAILED",
                    null,
                    "Sự cố số dư tài khoản"
            );
            return ResponseEntity.ok(response);
        }

        // Thanh toán thành công — tạo transactionId giả lập
        var transactionId = UUID.randomUUID().toString();
        log.info("✅ Thanh toán thành công: orderId={}, transactionId={}",
                request.orderId(), transactionId);

        var response = new PaymentResponse(
                request.orderId(),
                "SUCCESS",
                transactionId,
                "Thanh toán thành công"
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Xử lý hoàn tiền.
     *
     * @param request chứa orderId, amount
     * @return RefundResponse luôn trả về REFUNDED
     */
    @PostMapping("/refund")
    public ResponseEntity<RefundResponse> refundPayment(@RequestBody RefundRequest request) {
        log.info("💰 Xử lý hoàn tiền: orderId={}, amount={}", request.orderId(), request.amount());

        var response = new RefundResponse(
                request.orderId(),
                "REFUNDED",
                "Hoàn tiền thành công cho đơn hàng " + request.orderId()
        );

        log.info("✅ Hoàn tiền thành công: orderId={}", request.orderId());
        return ResponseEntity.ok(response);
    }
}
