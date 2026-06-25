package com.fooddelivery.notification.infrastructure.repository;

import com.fooddelivery.notification.infrastructure.persistence.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, ProcessedEvent.ProcessedEventId> {

    boolean existsByEventIdAndConsumer(UUID eventId, String consumer);

    default void markProcessed(UUID eventId, String consumer) {
        save(new ProcessedEvent(eventId, consumer));
    }
}
