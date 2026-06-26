package com.fooddelivery.notification.application;

import com.fooddelivery.notification.domain.NotificationLog;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-safe in-memory store for notification logs.
 *
 * <p>DESIGN TRADEOFF: Keeps the last 200 notifications in-memory only.
 * This is a deliberate tradeoff for the demo deliverables to simplify infrastructure setup (no separate DB needed for notifications).
 * In a production-grade system, this must persist to a durable database (e.g. PostgreSQL/MongoDB) or a dedicated notification store.</p>
 */
@Component
public class NotificationStore {

    private static final int MAX_SIZE = 200;

    private final Deque<NotificationLog> logs = new ConcurrentLinkedDeque<>();

    public void save(String eventType, String topic, String summary) {
        NotificationLog log = new NotificationLog(
                UUID.randomUUID().toString(),
                eventType,
                topic,
                summary,
                Instant.now()
        );
        logs.addFirst(log);
        // Trim to max size
        while (logs.size() > MAX_SIZE) {
            logs.pollLast();
        }
    }

    public Collection<NotificationLog> getAll() {
        return logs;
    }
}
