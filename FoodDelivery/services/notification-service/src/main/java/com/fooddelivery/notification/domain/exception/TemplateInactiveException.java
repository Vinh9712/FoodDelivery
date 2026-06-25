package com.fooddelivery.notification.domain.exception;

public class TemplateInactiveException extends RuntimeException {
    public TemplateInactiveException(String templateType) {
        super("Notification template of type " + templateType + " is inactive");
    }
}
