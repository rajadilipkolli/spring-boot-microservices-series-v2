/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.config.ApplicationProperties;
import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, OrderDto> kafkaTemplate;
    private final ApplicationProperties properties;
    private final JsonMapper jsonMapper;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final AtomicBoolean isPublishing = new AtomicBoolean(false);

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, OrderDto> kafkaTemplate,
            ApplicationProperties properties,
            JsonMapper jsonMapper,
            MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.publishedCounter = meterRegistry.counter("payment.outbox.events.published.count");
        this.failedCounter = meterRegistry.counter("payment.outbox.events.failed.count");
    }

    @Scheduled(fixedDelayString = "${application.outbox.publish-delay:500ms}")
    public void scheduledPublish() {
        if (!properties.getOutbox().isEnabled()) {
            return;
        }
        if (isPublishing.compareAndSet(false, true)) {
            try {
                publishPendingEvents();
            } finally {
                isPublishing.set(false);
            }
        }
    }

    public void publishPendingEvents() {
        String instanceId = UUID.randomUUID().toString();
        List<OutboxEventsRecord> events =
                outboxEventRepository.claimPendingEvents(
                        properties.getOutbox().getBatchSize(), instanceId);

        for (OutboxEventsRecord event : events) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEventsRecord event) {
        try {
            OrderDto dto = jsonMapper.readValue(event.getPayload(), OrderDto.class);
            long timeoutMs = properties.getOutbox().getSendTimeout().toMillis();
            kafkaTemplate
                    .send(event.getTopic(), event.getMessageKey(), dto)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);

            boolean marked = outboxEventRepository.markPublished(event.getId(), event.getVersion());
            if (marked) {
                publishedCounter.increment();
                log.debug("Successfully published outbox event {}", event.getId());
            } else {
                log.warn(
                        "Version mismatch marking event {} published (likely concurrent modification)",
                        event.getId());
            }
        } catch (Exception e) {
            log.error("Failed to publish outbox event {}", event.getId(), e);
            handleFailure(event, e.getMessage());
        }
    }

    private void handleFailure(OutboxEventsRecord event, String error) {
        failedCounter.increment();
        if (event.getRetryCount() + 1 >= properties.getOutbox().getMaxRetries()) {
            log.error("Outbox event {} exceeded max retries", event.getId());
            outboxEventRepository.markFailed(event.getId(), event.getVersion(), error);
        } else {
            outboxEventRepository.returnToPending(event.getId(), event.getVersion(), error);
        }
    }

    @Scheduled(cron = "${application.outbox.reaper-cron:0 */1 * * * *}")
    public void scheduledReap() {
        if (!properties.getOutbox().isEnabled()) {
            return;
        }
        OffsetDateTime threshold =
                OffsetDateTime.now().minus(properties.getOutbox().getLockTimeout());
        int reaped =
                outboxEventRepository.reapStaleProcessing(
                        threshold, properties.getOutbox().getMaxRetries());
        if (reaped > 0) {
            log.info("Reaped {} orphaned outbox events", reaped);
        }
    }
}
