package com.fooddelivery.notification.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.notification.domain.exception.MaxRetryExceededException;
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

    private static final int MAX_RETRY = 5;

    @Id
    private UUID id;

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

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Notification(UUID id, UUID userId, String type, Channel channel, RenderedContent content, EntityReference entityRef, JsonNode data) {
        this.id = id;
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
    }

    public static Notification create(UUID userId, String type, Channel channel, RenderedContent content, EntityReference entityRef, JsonNode data) {
        return new Notification(UuidCreator.nextUuidV7(), userId, type, channel, content, entityRef, data);
    }

    public EntityReference getEntityRef() {
        if (this.entityType == null || this.entityId == null) {
            return null;
        }
        return new EntityReference(this.entityType, this.entityId);
    }

    public void send() {
        this.sentAt = Instant.now();
    }

    public void markRead() {
        if (isRead) return;
        this.isRead = true;
        this.readAt = Instant.now();
    }

    public void retry() {
        if (retryCount >= MAX_RETRY) {
            throw new MaxRetryExceededException(this.id);
        }
        this.retryCount++;
    }

    public void recordFailure(String error) {
        this.lastError = error;
    }
}
