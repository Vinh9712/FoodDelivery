package com.fooddelivery.order.domain.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fooddelivery.order.domain.util.UuidCreator;

/**
 * Transactional Outbox Pattern entity.
 * <p>
 * Mỗi khi domain model thay đổi trạng thái, một OutboxEvent được tạo và lưu
 * trong cùng một database transaction. Outbox Poller (hoặc CDC) sẽ đọc các
 * event chưa publish và gửi lên message broker (Kafka).
 * </p>
 * <p>
 * Cấu trúc payload dạng JSONB cho phép lưu trữ linh hoạt mọi loại event
 * mà không cần thay đổi schema.
 * </p>
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Loại aggregate root sinh ra event, ví dụ: "Order" */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /** ID của aggregate root */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /** Loại sự kiện, ví dụ: "OrderCreated", "OrderPaid", "OrderCancelled" */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** Payload chi tiết của sự kiện, lưu dạng JSONB */
    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    /** Thời điểm event được publish ra message broker, null nếu chưa publish */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Factory method tạo OutboxEvent mới (chưa publish).
     *
     * @param aggregateType loại aggregate root (e.g. "Order")
     * @param aggregateId   ID của aggregate root
     * @param eventType     tên sự kiện (e.g. "OrderPaid")
     * @param payload       dữ liệu chi tiết của event
     */
    public static OutboxEvent create(String aggregateType, UUID aggregateId,
                                     String eventType, Map<String, Object> payload) {
        var event = new OutboxEvent();
        event.id = UuidCreator.nextUuidV7();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.publishedAt = null; // chưa publish
        event.createdAt = Instant.now();
        return event;
    }

    /**
     * Đánh dấu event đã được publish thành công.
     */
    public void markPublished() {
        this.publishedAt = Instant.now();
    }
}
