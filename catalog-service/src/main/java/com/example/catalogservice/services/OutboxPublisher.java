/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import com.example.catalogservice.config.ApplicationProperties;
import com.example.catalogservice.entities.OutboxEvent;
import com.example.catalogservice.entities.OutboxEventStatus;
import com.example.catalogservice.kafka.CatalogKafkaProducer;
import com.example.catalogservice.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final CatalogKafkaProducer catalogKafkaProducer;
    private final ApplicationProperties properties;
    private final Counter publishedEventCounter;
    private final Counter failedEventCounter;
    private final AtomicBoolean isPublishing = new AtomicBoolean(false);

    private final OutboxPublisher self;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            CatalogKafkaProducer catalogKafkaProducer,
            ApplicationProperties properties,
            MeterRegistry meterRegistry,
            @Lazy OutboxPublisher self) {
        this.outboxEventRepository = outboxEventRepository;
        this.catalogKafkaProducer = catalogKafkaProducer;
        this.properties = properties;
        this.self = self;

        this.publishedEventCounter =
                Counter.builder("outbox.events.published.count")
                        .description("Total number of outbox events published")
                        .register(meterRegistry);
        this.failedEventCounter =
                Counter.builder("outbox.events.failed.count")
                        .description("Total number of outbox events failed")
                        .register(meterRegistry);
    }

    /**
     * Claims and publishes up to 100 pending events, blocking until the publisher terminates. Skips
     * the run if this instance is already publishing. Publisher errors are suppressed, and the
     * local publishing guard is released even when the run fails.
     */
    @Scheduled(fixedDelayString = "${application.outbox.publish-delay:5000}")
    @SchedulerLock(name = "scheduledPublishLock")
    public void scheduledPublish() {
        if (isPublishing.compareAndSet(false, true)) {
            try {
                this.publishEvents()
                        .doOnNext(event -> log.debug("Published outbox event: {}", event.getId()))
                        .doOnError(
                                ex ->
                                        log.error(
                                                "Error occurred while publishing outbox events",
                                                ex))
                        .onErrorComplete()
                        .blockLast();
            } finally {
                isPublishing.set(false);
            }
        }
    }

    public Flux<OutboxEvent> publishEvents() {
        return this.self.claimEvents().flatMap(this::publishEvent);
    }

    @Transactional
    public Flux<OutboxEvent> claimEvents() {
        return outboxEventRepository.claimPendingEvents(100);
    }

    private Mono<OutboxEvent> publishEvent(OutboxEvent event) {
        log.info("Sending outbox event to Kafka: {}", event.getId());
        return catalogKafkaProducer
                .send(event.getAggregateId(), event.getPayload().content())
                .flatMap(
                        success -> {
                            if (success) {
                                return handleSuccess(event);
                            } else {
                                return handleFailure(event, "Kafka send returned false");
                            }
                        })
                .onErrorResume(ex -> handleFailure(event, ex.getMessage()));
    }

    /**
     * Marks the supplied event as published with the current processing time and saves it.
     *
     * @return the saved event, or an empty Mono on an optimistic locking conflict; other
     *     persistence errors propagate to the caller
     */
    private Mono<OutboxEvent> handleSuccess(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.PUBLISHED).setProcessedAt(OffsetDateTime.now());
        return outboxEventRepository
                .save(event)
                .doOnSuccess(saved -> publishedEventCounter.increment())
                .onErrorResume(
                        OptimisticLockingFailureException.class,
                        ex -> {
                            log.warn(
                                    "Optimistic locking conflict on OutboxEvent {}: skipping",
                                    event.getId());
                            return Mono.empty();
                        });
    }

    /**
     * Records a publishing failure and saves the event. Below the configured retry limit, marks it
     * pending and increments its retry count; otherwise marks it failed.
     *
     * @param error the message stored on the event
     * @return the saved event, or an empty Mono on an optimistic locking conflict; other
     *     persistence errors propagate to the caller
     */
    private Mono<OutboxEvent> handleFailure(OutboxEvent event, String error) {
        if (event.getRetryCount() < properties.outbox().getMaxRetries()) {
            log.warn("Retrying event {}: {}", event.getId(), error);
            event.setStatus(OutboxEventStatus.PENDING)
                    .setRetryCount(event.getRetryCount() + 1)
                    .setErrorMessage(error);
        } else {
            log.error("Event {} failed after max retries: {}", event.getId(), error);
            event.setStatus(OutboxEventStatus.FAILED).setErrorMessage(error);
            return outboxEventRepository
                    .save(event)
                    .doOnSuccess(saved -> failedEventCounter.increment())
                    .onErrorResume(
                            OptimisticLockingFailureException.class,
                            ex -> {
                                log.warn(
                                        "Optimistic locking conflict on OutboxEvent {}: skipping",
                                        event.getId());
                                return Mono.empty();
                            });
        }
        return outboxEventRepository
                .save(event)
                .onErrorResume(
                        OptimisticLockingFailureException.class,
                        ex -> {
                            log.warn(
                                    "Optimistic locking conflict on OutboxEvent {}: skipping",
                                    event.getId());
                            return Mono.empty();
                        });
    }

    /**
     * Reclaims processing events locked longer than the configured lock timeout and waits for
     * completion. Increments retry counts, marking events failed when the new count reaches the
     * retry limit and pending otherwise. Errors emitted by the publisher are suppressed.
     */
    @Scheduled(cron = "${application.outbox.reaper-cron:0 */1 * * * *}")
    @SchedulerLock(name = "scheduledReapLock")
    public void scheduledReap() {
        log.debug("Running outbox reaper");
        OffsetDateTime threshold = OffsetDateTime.now().minus(properties.outbox().getLockTimeout());
        outboxEventRepository
                .reapOrphanedEvents(threshold, properties.outbox().getMaxRetries())
                .doOnNext(
                        count -> {
                            if (count > 0) {
                                log.info("Reaped {} orphaned outbox events", count);
                            }
                        })
                .doOnError(ex -> log.error("Error occurred while reaping outbox events", ex))
                .onErrorComplete()
                .block();
    }
}
