package com.fooddelivery.delivery.infrastructure.repository;

import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    Optional<Delivery> findByOrderId(UUID orderId);

    Page<Delivery> findByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);

    Page<Delivery> findByDriverIdAndStatusOrderByCreatedAtDesc(
            UUID driverId, DeliveryStatus status, Pageable pageable);

    Page<Delivery> findByStatusOrderByCreatedAtDesc(DeliveryStatus status, Pageable pageable);

    Optional<Delivery> findFirstByDriverIdAndStatusInOrderByUpdatedAtDesc(
            UUID driverId, Collection<DeliveryStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.orderId = :orderId")
    Optional<Delivery> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :deliveryId")
    Optional<Delivery> findByIdForUpdate(@Param("deliveryId") UUID deliveryId);

    @Query("""
            select count(d) from Delivery d
            where d.driverId = :driverId
              and d.status in :statuses
              and (:excludeId is null or d.id <> :excludeId)
            """)
    long countActiveByDriver(
            @Param("driverId") UUID driverId,
            @Param("statuses") List<DeliveryStatus> statuses,
            @Param("excludeId") UUID excludeId);

    @Query("""
            select d.id from Delivery d
            where d.status = com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus.FINDING_DRIVER
              and (d.nextAssignmentAt is null or d.nextAssignmentAt <= :now)
            order by d.createdAt
            """)
    List<UUID> findDueAssignmentIds(@Param("now") Instant now, Pageable pageable);
}
