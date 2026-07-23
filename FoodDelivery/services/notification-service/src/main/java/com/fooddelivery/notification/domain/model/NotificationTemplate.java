package com.fooddelivery.notification.domain.model;

import com.fooddelivery.notification.domain.exception.TemplateInactiveException;
import com.fooddelivery.notification.domain.model.valueobject.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;
import com.fooddelivery.notification.domain.util.UuidCreator;

@Entity
@Table(name = "notification_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplate {

    @Id
    private UUID id;

    @Column(name = "type", nullable = false, unique = true, length = 50)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel;

    @Column(name = "title_template", nullable = false)
    private String titleTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public NotificationTemplate(UUID id, String type, Channel channel, TemplateContent content) {
        this.id = id;
        this.type = type;
        this.channel = channel;
        this.titleTemplate = content.titleTemplate();
        this.bodyTemplate = content.bodyTemplate();
        this.isActive = true;
    }

    public static NotificationTemplate create(String type, Channel channel, TemplateContent content) {
        return new NotificationTemplate(UuidCreator.nextUuidV7(), type, channel, content);
    }

    public TemplateContent getContent() {
        return new TemplateContent(this.titleTemplate, this.bodyTemplate);
    }

    public void updateContent(TemplateContent content) {
        this.titleTemplate = content.titleTemplate();
        this.bodyTemplate = content.bodyTemplate();
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public RenderedContent render(Map<String, Object> variables) {
        if (!isActive) {
            throw new TemplateInactiveException(this.type);
        }
        String title = titleTemplate;
        String body = bodyTemplate;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                String valueStr = entry.getValue() != null ? entry.getValue().toString() : "";
                title = title.replace(placeholder, valueStr);
                body = body.replace(placeholder, valueStr);
            }
        }
        return new RenderedContent(title, body);
    }
}
