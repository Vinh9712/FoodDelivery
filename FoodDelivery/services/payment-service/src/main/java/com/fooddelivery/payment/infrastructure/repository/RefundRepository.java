// RefundRepository.java
package com.fooddelivery.payment.infrastructure.repository;

import com.fooddelivery.payment.domain.model.Refund;
import com.fooddelivery.payment.domain.model.valueobject.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    // ✅ Sửa từ findByPaymentId -> findByPayment_Id
    List<Refund> findByPayment_Id(UUID paymentId);

    // ✅ Sửa từ findByPaymentIdAndStatus -> findByPayment_IdAndStatus
    List<Refund> findByPayment_IdAndStatus(UUID paymentId, RefundStatus status);

    // Có thể thêm các method khác nếu cần
    List<Refund> findByStatus(RefundStatus status);
}