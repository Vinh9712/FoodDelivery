package com.fooddelivery.commonevents;

import java.time.Instant;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public abstract class BaseEvent {
    private UUID eventId;
    private String eventType;
    private int eventVersion;
    private Instant occurredAt;

    protected BaseEvent() {
        this.eventId = UuidCreator.getTimeOrderedEpoch();
        this.occurredAt = Instant.now();
    }

    protected BaseEvent(String eventType, int eventVersion) {
        this();
        this.eventType = eventType;
        this.eventVersion = eventVersion;
    }
}
