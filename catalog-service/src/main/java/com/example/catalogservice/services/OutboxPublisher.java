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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final CatalogKafkaProducer catalogKafkaProducer;
    private final ApplicationProperties properties;
    private final Counter publishedEventCounter;
    private final Counter failedEventCounter;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            CatalogKafkaProducer catalogKafkaProducer,
            ApplicationProperties properties,
            MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.catalogKafkaProducer = catalogKafkaProducer;
        this.properties = properties;

        this.publishedEventCounter =
                Counter.builder("outbox.events.published.count")
                        .description("Total number of outbox events published")
                        .register(meterRegistry);
        this.failedEventCounter =
                Counter.builder("outbox.events.failed.count")
                        .description("Total number of outbox events failed")
                        .register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${application.outbox.publish-delay:5000}")
    public void scheduledPublish() {
        this.publishEvents()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> log.debug("Published outbox event: {}", event.getId()),
                        ex -> log.error("Error occurred while publishing outbox events", ex));
    }

    public Flux<OutboxEvent> publishEvents() {
        return this.claimEvents().flatMap(this::publishEvent);
    }

    @Transactional
    public Flux<OutboxEvent> claimEvents() {
        return outboxEventRepository.claimPendingEvents(100);
    }

    private Mono<OutboxEvent> publishEvent(OutboxEvent event) {
        log.info("Sending outbox event to Kafka: {}", event.getId());
        return catalogKafkaProducer
                .send(event.getAggregateId(), event.getPayload().toString())
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

    private Mono<OutboxEvent> handleSuccess(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.PUBLISHED).setProcessedAt(OffsetDateTime.now());
        return outboxEventRepository
                .save(event)
                .doOnSuccess(saved -> publishedEventCounter.increment());
    }

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
                    .doOnSuccess(saved -> failedEventCounter.increment());
        }
        return outboxEventRepository.save(event);
    }

    @Scheduled(cron = "${application.outbox.reaper-cron:0 */1 * * * *}")
    public void scheduledReap() {
        log.debug("Running outbox reaper");
        OffsetDateTime threshold = OffsetDateTime.now().minus(properties.outbox().getLockTimeout());
        outboxEventRepository
                .reapOrphanedEvents(threshold, properties.outbox().getMaxRetries())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.info("Reaped {} orphaned outbox events", count);
                            }
                        },
                        ex -> log.error("Error occurred while reaping outbox events", ex));
    }
}
