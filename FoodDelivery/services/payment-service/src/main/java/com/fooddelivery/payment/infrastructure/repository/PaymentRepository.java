// PaymentRepository.java
package com.fooddelivery.payment.infrastructure.repository;

import com.fooddelivery.payment.domain.model.Payment;
import com.fooddelivery.payment.domain.model.valueobject.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    List<Payment> findByCustomerId(UUID customerId);

    List<Payment> findByCustomerIdAndStatus(UUID customerId, PaymentStatus status);

    Optional<Payment> findByGatewayTransactionId(String transactionId);

    boolean existsByOrderId(UUID orderId);

    List<Payment> findByStatus(PaymentStatus status);
}