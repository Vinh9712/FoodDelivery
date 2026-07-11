package com.fooddelivery.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.notification.api.dto.NotificationRequest;
import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.domain.model.valueobject.Channel;
import com.fooddelivery.notification.domain.model.valueobject.EntityReference;
import com.fooddelivery.notification.domain.model.valueobject.RenderedContent;
import com.fooddelivery.notification.infrastructure.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NotificationJobService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Notification enqueue(NotificationRequest request) {
        Channel channel = parseChannel(request.channel());
        String requestKey = requestKey(request, channel);
        return notificationRepository.findByRequestKey(requestKey)
                .orElseGet(() -> notificationRepository.save(Notification.create(
                        requestKey,
                        request.customerId(),
                        "ORDER_NOTIFICATION",
                        channel,
                        new RenderedContent(request.subject(), request.message()),
                        new EntityReference("Order", request.orderId()),
                        objectMapper.createObjectNode().put("orderId", request.orderId().toString()))));
    }

    private Channel parseChannel(String value) {
        if (value == null || value.isBlank()) {
            return Channel.IN_APP;
        }
        try {
            return Channel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported notification channel: " + value, ex);
        }
    }

    private String requestKey(NotificationRequest request, Channel channel) {
        String canonical = request.orderId() + "|" + request.customerId() + "|" + channel
                + "|" + request.subject() + "|" + request.message();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
