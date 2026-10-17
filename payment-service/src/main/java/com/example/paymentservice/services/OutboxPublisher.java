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
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
    private final Counter publicationMismatchCounter;
    private final AtomicBoolean isPublishing = new AtomicBoolean(false);

    /**
     * Creates the outbox publisher with event storage, Kafka delivery, configuration, JSON mapping,
     * and publication counters.
     */
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
        this.publicationMismatchCounter =
                meterRegistry.counter("payment.outbox.events.publication.mismatch.count");
    }

    /** Publishes one batch when the outbox is enabled and no local publication is active. */
    @Scheduled(fixedDelayString = "${application.outbox.publish-delay:500ms}")
    @SchedulerLock(name = "payment_outbox_publish", lockAtMostFor = "1m", lockAtLeastFor = "100ms")
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

    /**
     * Claims one configured batch and sends its replies to Kafka, awaiting each send up to the
     * configured timeout. Per-event runtime failures are caught so later events can proceed; claim
     * failures propagate. Runs even when scheduled publication is disabled. Delivery can repeat if
     * Kafka accepts a message but its publication status cannot be persisted.
     */
    public void publishPendingEvents() {
        String instanceId = UUID.randomUUID().toString();
        List<OutboxEventsRecord> events =
                outboxEventRepository.claimPendingEvents(
                        properties.getOutbox().getBatchSize(), instanceId);

        for (OutboxEventsRecord event : events) {
            try {
                publishEvent(event);
            } catch (RuntimeException e) {
                log.error("Failed to finish publishing outbox event {}", event.getId(), e);
            }
        }
    }

    /**
     * Sends a stored reply and marks it published using the claimed version. Decode or send
     * failures attempt requeue or failure marking and are caught. Publication status exceptions
     * after delivery trigger a requeue attempt and are rethrown; a version mismatch leaves the
     * event unchanged.
     */
    private void publishEvent(OutboxEventsRecord event) {
        try {
            OrderDto dto = jsonMapper.readValue(event.getPayload(), OrderDto.class);
            long timeoutMs = properties.getOutbox().getSendTimeout().toMillis();
            kafkaTemplate
                    .send(event.getTopic(), event.getMessageKey(), dto)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("Failed to publish outbox event {}", event.getId(), e);
            try {
                handleFailure(event, e.getMessage());
            } catch (RuntimeException failure) {
                log.error(
                        "Failed to record publication failure for outbox event {}",
                        event.getId(),
                        failure);
            }
            return;
        }

        try {
            if (outboxEventRepository.markPublished(event.getId(), event.getVersion())) {
                publishedCounter.increment();
                log.debug("Successfully published outbox event {}", event.getId());
            } else {
                publicationMismatchCounter.increment();
                log.warn(
                        "Outbox event {} delivered to Kafka but publication status did not match",
                        event.getId());
            }
        } catch (RuntimeException e) {
            publicationMismatchCounter.increment();
            log.error(
                    "Outbox event {} delivered to Kafka but publication status update failed",
                    event.getId(),
                    e);
            try {
                outboxEventRepository.returnToPending(
                        event.getId(), event.getVersion(), e.getMessage());
            } catch (RuntimeException requeueFailure) {
                e.addSuppressed(requeueFailure);
                log.error(
                        "Failed to requeue delivered outbox event {}",
                        event.getId(),
                        requeueFailure);
            }
            throw e;
        }
    }

    /**
     * Marks FAILED when one more retry would reach the limit; otherwise requeues and increments
     * retries. Both updates require the claimed version; persistence exceptions propagate.
     */
    private void handleFailure(OutboxEventsRecord event, String error) {
        failedCounter.increment();
        if (event.getRetryCount() + 1 >= properties.getOutbox().getMaxRetries()) {
            log.error("Outbox event {} exceeded max retries", event.getId());
            outboxEventRepository.markFailed(event.getId(), event.getVersion(), error);
        } else {
            outboxEventRepository.returnToPending(event.getId(), event.getVersion(), error);
        }
    }

    /**
     * Reclaims PROCESSING events locked longer than the configured lock timeout when enabled.
     * Events reaching the retry limit become FAILED; the rest return to PENDING. Database failures
     * propagate.
     */
    @Scheduled(cron = "${application.outbox.reaper-cron:0 */1 * * * *}")
    @SchedulerLock(name = "payment_outbox_reap", lockAtMostFor = "10m", lockAtLeastFor = "10s")
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
