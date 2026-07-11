package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.api.dto.DeliveryRequest;
import com.fooddelivery.delivery.api.dto.DeliveryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Delivery Service MVP Controller.
 * <p>
 * Triển khai tối giản để phục vụ kiểm thử luồng Saga:
 * <ul>
 *   <li>Nếu địa chỉ chứa từ khóa "Invalid" → FAILED ("Vùng giao hàng không hỗ trợ")</li>
 *   <li>Ngược lại → ASSIGNED kèm driverId ngẫu nhiên</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/internal/v1/deliveries")
public class DeliveryMvpController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryMvpController.class);

    /**
     * Lập lịch giao vận cho đơn hàng.
     *
     * @param request chứa orderId, deliveryAddressSnapshot
     * @return DeliveryResponse với status ASSIGNED hoặc FAILED
     */
    @PostMapping
    public ResponseEntity<DeliveryResponse> scheduleDelivery(@RequestBody DeliveryRequest request) {
        log.info("📥 Nhận yêu cầu giao vận: orderId={}, address={}",
                request.orderId(), request.deliveryAddressSnapshot());

        // Kiểm tra từ khóa "Invalid" trong địa chỉ — giả lập vùng giao hàng không hỗ trợ
        String address = request.deliveryAddressSnapshot();
        if (address != null && address.contains("Invalid")) {
            log.warn("❌ Giao vận thất bại: địa chỉ '{}' chứa vùng không hỗ trợ", address);

            var response = new DeliveryResponse(
                    request.orderId(),
                    "FAILED",
                    null,
                    "Vùng giao hàng không hỗ trợ"
            );
            return ResponseEntity.ok(response);
        }

        // Giao vận thành công — tạo driverId ngẫu nhiên
        var driverId = UUID.randomUUID();
        log.info("✅ Phân bổ tài xế thành công: orderId={}, driverId={}, status=DELIVERING",
                request.orderId(), driverId);

        var response = new DeliveryResponse(
                request.orderId(),
                "ASSIGNED",
                driverId,
                "Tài xế đã được phân bổ, đang giao hàng"
        );
        return ResponseEntity.ok(response);
    }
}
