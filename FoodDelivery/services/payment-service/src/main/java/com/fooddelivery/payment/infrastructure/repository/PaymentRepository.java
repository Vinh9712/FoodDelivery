package com.fooddelivery.payment.infrastructure.repository;

import com.fooddelivery.payment.domain.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
