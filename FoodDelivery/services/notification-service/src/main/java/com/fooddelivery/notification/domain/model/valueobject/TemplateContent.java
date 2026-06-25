package com.fooddelivery.notification.domain.model.valueobject;

public record TemplateContent(String titleTemplate, String bodyTemplate) {
    public TemplateContent {
        if (titleTemplate == null || bodyTemplate == null) {
            throw new IllegalArgumentException("Title and body templates cannot be null");
        }
    }
}
