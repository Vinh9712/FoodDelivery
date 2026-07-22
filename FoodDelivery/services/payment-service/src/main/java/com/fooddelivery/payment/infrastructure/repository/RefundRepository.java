package com.fooddelivery.payment.infrastructure.repository;

import com.fooddelivery.payment.domain.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    Optional<Refund> findByPayment_Id(UUID paymentId);

    long countByPayment_Id(UUID paymentId);
}
