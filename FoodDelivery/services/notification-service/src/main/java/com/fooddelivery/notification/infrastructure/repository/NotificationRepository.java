package com.fooddelivery.notification.infrastructure.repository;

import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.domain.model.valueobject.NotificationStatus;
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
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    Optional<Notification> findByRequestKey(String requestKey);

    List<Notification> findTop200ByOrderByCreatedAtDesc();

    @Query("""
            select notification.id from Notification notification
            where notification.status in :statuses
              and notification.scheduledAt <= :now
              and (notification.nextAttemptAt is null or notification.nextAttemptAt <= :now)
            order by notification.createdAt
            """)
    List<UUID> findDueJobIds(
            @Param("statuses") Collection<NotificationStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from Notification notification where notification.id = :notificationId")
    Optional<Notification> findByIdForUpdate(@Param("notificationId") UUID notificationId);
}
