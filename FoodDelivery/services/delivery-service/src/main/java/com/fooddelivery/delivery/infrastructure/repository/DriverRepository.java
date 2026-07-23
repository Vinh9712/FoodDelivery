package com.fooddelivery.delivery.infrastructure.repository;

import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DriverStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    Optional<Driver> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from Driver d
            where d.available = true
              and d.isOnline = true
              and d.status = :status
            order by d.id
            """)
    List<Driver> findAssignmentCandidatesForUpdate(
            @Param("status") DriverStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Driver d where d.id = :driverId")
    Optional<Driver> findByIdForUpdate(@Param("driverId") UUID driverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Driver d where d.userId = :userId")
    Optional<Driver> findByUserIdForUpdate(@Param("userId") UUID userId);
}
