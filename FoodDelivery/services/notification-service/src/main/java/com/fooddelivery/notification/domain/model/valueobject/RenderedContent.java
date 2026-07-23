package com.fooddelivery.notification.domain.model.valueobject;

public record RenderedContent(String title, String body) {
    public RenderedContent {
        if (title == null || body == null) {
            throw new IllegalArgumentException("Rendered title and body cannot be null");
        }
    }
}
