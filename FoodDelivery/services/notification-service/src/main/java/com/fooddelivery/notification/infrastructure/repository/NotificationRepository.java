package com.fooddelivery.notification.infrastructure.repository;

import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.domain.model.valueobject.NotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(UUID userId);

    @Query("""
            select n from Notification n
            where n.userId = :userId and n.isRead = :isRead
            order by n.createdAt desc
            """)
    Page<Notification> findByUserIdAndReadFlag(
            @Param("userId") UUID userId,
            @Param("isRead") boolean isRead,
            Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    Optional<Notification> findByRequestKey(String requestKey);

    List<Notification> findTop200ByOrderByCreatedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.isRead = true, n.readAt = :now, n.updatedAt = :now
            where n.userId = :userId and n.isRead = false
            """)
    int markAllReadByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

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
