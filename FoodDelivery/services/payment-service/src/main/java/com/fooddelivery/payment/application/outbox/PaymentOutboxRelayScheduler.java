package com.fooddelivery.payment.application.outbox;

import com.fooddelivery.payment.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.payment.outbox.relay.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PaymentOutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentOutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${app.payment.outbox.relay.poll-delay:2s}")
    public void relayPendingEvents() {
        for (UUID eventId : outboxEventRepository.findDueEventIds(
                Instant.now(), PageRequest.of(0, 50))) {
            publisher.publishOne(eventId);
        }
    }
}
