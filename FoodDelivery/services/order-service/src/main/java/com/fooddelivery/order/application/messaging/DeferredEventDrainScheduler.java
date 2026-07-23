package com.fooddelivery.order.application.messaging;

import com.fooddelivery.order.infrastructure.persistence.DeferredIntegrationEvent;
import com.fooddelivery.order.infrastructure.repository.ConsumedAggregateSequenceRepository;
import com.fooddelivery.order.infrastructure.repository.DeferredIntegrationEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Periodically re-checks deferred events. Never skips a sequence.
 * After the gap window expires, publishes the raw event to DLT, marks {@code DEAD_LETTER},
 * increments {@code event_sequence_gap_total}, and does not advance the aggregate cursor.
 */
@Component
@ConditionalOnProperty(
        name = "app.order.inbox.deferred-drain.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class DeferredEventDrainScheduler {

    public static final String GAP_METRIC = "event_sequence_gap_total";

    private final DeferredIntegrationEventRepository deferredRepository;
    private final ConsumedAggregateSequenceRepository sequenceRepository;
    private final SequencedEventProcessor processor;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Clock clock;
    private final Counter gapCounter;
    private final Duration gapWindow;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final int batchSize;
    private final String dltTopic;
    private final Map<String, SequencedConsumer> consumersByName;

    @Autowired
    public DeferredEventDrainScheduler(
            DeferredIntegrationEventRepository deferredRepository,
            ConsumedAggregateSequenceRepository sequenceRepository,
            SequencedEventProcessor processor,
            KafkaTemplate<String, Object> kafkaTemplate,
            Clock clock,
            MeterRegistry meterRegistry,
            List<SequencedConsumer> sequencedConsumers,
            @Value("${app.order.inbox.gap-window:10m}") Duration gapWindow,
            @Value("${app.order.inbox.initial-backoff:5s}") Duration initialBackoff,
            @Value("${app.order.inbox.max-backoff:1m}") Duration maxBackoff,
            @Value("${app.order.inbox.deferred-batch-size:50}") int batchSize,
            @Value("${app.order.inbox.dlt-topic:delivery.events.v1.DLT}") String dltTopic) {
        this.deferredRepository = deferredRepository;
        this.sequenceRepository = sequenceRepository;
        this.processor = processor;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.gapWindow = gapWindow;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.batchSize = batchSize;
        this.dltTopic = dltTopic;
        this.consumersByName = sequencedConsumers.stream()
                .collect(Collectors.toMap(SequencedConsumer::consumerName, Function.identity(), (a, b) -> a));
        this.gapCounter = Counter.builder(GAP_METRIC)
                .description("Integration event sequence gaps dead-lettered after gap window")
                .register(meterRegistry);
    }

    /**
     * Test-friendly constructor allowing an explicit consumer map / metric registry.
     */
    DeferredEventDrainScheduler(
            DeferredIntegrationEventRepository deferredRepository,
            ConsumedAggregateSequenceRepository sequenceRepository,
            SequencedEventProcessor processor,
            KafkaTemplate<String, Object> kafkaTemplate,
            Clock clock,
            MeterRegistry meterRegistry,
            Map<String, SequencedConsumer> consumersByName,
            Duration gapWindow,
            Duration initialBackoff,
            Duration maxBackoff,
            int batchSize,
            String dltTopic) {
        this.deferredRepository = deferredRepository;
        this.sequenceRepository = sequenceRepository;
        this.processor = processor;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.consumersByName = consumersByName;
        this.gapWindow = gapWindow;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.batchSize = batchSize;
        this.dltTopic = dltTopic;
        this.gapCounter = Counter.builder(GAP_METRIC)
                .description("Integration event sequence gaps dead-lettered after gap window")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.order.inbox.deferred-scan-delay:15s}")
    public void drainDueDeferred() {
        Instant now = clock.instant();
        List<DeferredIntegrationEvent> due = deferredRepository.findDueWaiting(now, PageRequest.of(0, batchSize));
        for (DeferredIntegrationEvent deferred : due) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Deferred drain interrupted; stopping current poll");
                return;
            }
            try {
                processOne(deferred.getId());
            } catch (Exception ex) {
                log.warn(
                        "Failed deferred drain for eventId={} sequence={}: {}",
                        deferred.getEventId(),
                        deferred.getAggregateSequence(),
                        ex.toString());
            }
        }
    }

    @Transactional
    public void processOne(UUID deferredId) {
        DeferredIntegrationEvent deferred = deferredRepository.findById(deferredId).orElse(null);
        if (deferred == null || !deferred.isWaiting()) {
            return;
        }

        Instant now = clock.instant();
        long last = sequenceRepository
                .findCurrent(deferred.getConsumerName(), deferred.getAggregateType(), deferred.getAggregateId())
                .orElse(0L);

        long sequence = deferred.getAggregateSequence();

        if (sequence <= last) {
            // Already applied via another path — drop deferred shell without advancing again.
            deferredRepository.delete(deferred);
            return;
        }

        if (sequence == last + 1) {
            SequencedConsumer consumer = consumersByName.get(deferred.getConsumerName());
            if (consumer == null) {
                log.warn("No SequencedConsumer registered for {}", deferred.getConsumerName());
                deferred.scheduleRetry(now.plus(initialBackoff), "no consumer registered");
                deferredRepository.save(deferred);
                return;
            }
            processor.tryDrainNext(
                    deferred.getConsumerName(),
                    deferred.getAggregateType(),
                    deferred.getAggregateId(),
                    consumer::handle);
            return;
        }

        // sequence > last + 1 — still a gap; never skip.
        if (isGapExpired(deferred, now)) {
            deadLetterGap(deferred, now, last);
            return;
        }

        Instant nextAttempt = nextBackoff(deferred, now);
        deferred.scheduleRetry(nextAttempt, "waiting for predecessor sequence " + (last + 1));
        deferredRepository.save(deferred);
    }

    private boolean isGapExpired(DeferredIntegrationEvent deferred, Instant now) {
        Instant deadline = deferred.getReceivedAt().plus(gapWindow);
        return !now.isBefore(deadline);
    }

    private Instant nextBackoff(DeferredIntegrationEvent deferred, Instant now) {
        long multiplier = 1L << Math.min(deferred.getAttempts(), 10);
        Duration delay = initialBackoff.multipliedBy(multiplier);
        if (delay.compareTo(maxBackoff) > 0) {
            delay = maxBackoff;
        }
        Instant candidate = now.plus(delay);
        Instant gapDeadline = deferred.getReceivedAt().plus(gapWindow);
        return candidate.isAfter(gapDeadline) ? gapDeadline : candidate;
    }

    private void deadLetterGap(DeferredIntegrationEvent deferred, Instant now, long lastApplied) {
        String reason = "sequence gap expired: waiting for " + (lastApplied + 1)
                + " but held " + deferred.getAggregateSequence();
        try {
            kafkaTemplate.send(MessageBuilder.withPayload(deferred.getEventJson())
                    .setHeader(KafkaHeaders.TOPIC, dltTopic)
                    .setHeader(KafkaHeaders.KEY, deferred.getAggregateId().toString())
                    .setHeader("x-consumer-name", deferred.getConsumerName())
                    .setHeader("x-aggregate-type", deferred.getAggregateType())
                    .setHeader("x-aggregate-id", deferred.getAggregateId().toString())
                    .setHeader("x-aggregate-sequence", String.valueOf(deferred.getAggregateSequence()))
                    .setHeader("x-event-id", deferred.getEventId().toString())
                    .setHeader("x-gap-reason", reason)
                    .setHeader("x-last-applied-sequence", String.valueOf(lastApplied))
                    .build());
        } catch (Exception ex) {
            log.error("Failed to publish deferred event {} to DLT topic {}", deferred.getEventId(), dltTopic, ex);
            deferred.scheduleRetry(now.plus(initialBackoff), "DLT publish failed: " + ex.getMessage());
            deferredRepository.save(deferred);
            return;
        }

        deferred.markDeadLetter(now, reason);
        deferredRepository.save(deferred);
        gapCounter.increment();
        log.error(
                "Dead-lettered deferred event {} sequence {} for {}:{} after gap window; cursor stays at {}",
                deferred.getEventId(),
                deferred.getAggregateSequence(),
                deferred.getAggregateType(),
                deferred.getAggregateId(),
                lastApplied);
        // Intentionally do NOT advance the aggregate cursor.
    }
}
