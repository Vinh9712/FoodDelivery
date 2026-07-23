package com.fooddelivery.notification.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.notification.domain.model.valueobject.*;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.notification.domain.util.UuidCreator;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    private UUID id;

    @Column(name = "request_key", nullable = false, unique = true, length = 64)
    private String requestKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Type(JsonType.class)
    @Column(name = "data", columnDefinition = "jsonb")
    private JsonNode data;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NotificationStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Notification(UUID id, String requestKey, UUID userId, String type, Channel channel,
                        RenderedContent content, EntityReference entityRef, JsonNode data) {
        this.id = id;
        this.requestKey = requestKey;
        this.userId = userId;
        this.type = type;
        this.channel = channel;
        this.title = content.title();
        this.body = content.body();
        if (entityRef != null) {
            this.entityType = entityRef.entityType();
            this.entityId = entityRef.entityId();
        }
        this.data = data;
        this.isRead = false;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.scheduledAt = this.createdAt;
        this.nextAttemptAt = this.createdAt;
        this.status = NotificationStatus.PENDING;
    }

    public static Notification create(String requestKey, UUID userId, String type, Channel channel,
                                      RenderedContent content, EntityReference entityRef, JsonNode data) {
        return new Notification(UuidCreator.nextUuidV7(), requestKey, userId, type, channel,
                content, entityRef, data);
    }

    public EntityReference getEntityRef() {
        if (this.entityType == null || this.entityId == null) {
            return null;
        }
        return new EntityReference(this.entityType, this.entityId);
    }

    public void markSending() {
        if (status != NotificationStatus.PENDING && status != NotificationStatus.RETRY_SCHEDULED) {
            throw new IllegalStateException("Notification is not dispatchable: " + status);
        }
        this.status = NotificationStatus.SENDING;
        this.updatedAt = Instant.now();
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
        this.updatedAt = this.sentAt;
    }

    public void markRead() {
        if (isRead) {
            return;
        }
        this.isRead = true;
        this.readAt = Instant.now();
        this.updatedAt = this.readAt;
    }

    public void recordFailure(String error, Instant retryAt, int maxAttempts) {
        this.retryCount++;
        this.lastError = truncate(error);
        this.updatedAt = Instant.now();
        if (retryCount >= maxAttempts) {
            this.status = NotificationStatus.DEAD_LETTER;
            this.failedAt = this.updatedAt;
            this.nextAttemptAt = null;
        } else {
            this.status = NotificationStatus.RETRY_SCHEDULED;
            this.nextAttemptAt = retryAt;
        }
    }

    public boolean canDispatch(Instant now) {
        return (status == NotificationStatus.PENDING || status == NotificationStatus.RETRY_SCHEDULED)
                && !scheduledAt.isAfter(now)
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown dispatch error";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
