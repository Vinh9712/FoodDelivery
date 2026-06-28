package com.fooddelivery.authentication.infrastructure.persistence;

import com.fooddelivery.authentication.infrastructure.persistence.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY created_at ASC LIMIT 10 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findUnpublishedEventsForUpdate();

    @Query(value = "SELECT id FROM outbox_events WHERE published_at IS NULL ORDER BY created_at ASC LIMIT 10", nativeQuery = true)
    List<UUID> findUnpublishedEventIds();

    @Query(value = "SELECT * FROM outbox_events WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<OutboxEvent> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
