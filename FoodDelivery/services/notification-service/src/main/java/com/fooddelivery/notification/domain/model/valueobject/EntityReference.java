package com.fooddelivery.notification.domain.model.valueobject;

import java.util.UUID;

public record EntityReference(String entityType, UUID entityId) {
    public EntityReference {
        if (entityType == null || entityId == null) {
            throw new IllegalArgumentException("Entity type and ID cannot be null");
        }
    }
}
