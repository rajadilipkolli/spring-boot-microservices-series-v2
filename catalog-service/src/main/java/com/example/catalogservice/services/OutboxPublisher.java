/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

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
import reactor.core.scheduler.Schedulers;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final CatalogKafkaProducer catalogKafkaProducer;
    private final Counter publishedEventCounter;
    private final Counter failedEventCounter;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            CatalogKafkaProducer catalogKafkaProducer,
            MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.catalogKafkaProducer = catalogKafkaProducer;

        this.publishedEventCounter =
                Counter.builder("outbox.events.published.count")
                        .description("Total number of outbox events published")
                        .register(meterRegistry);
        this.failedEventCounter =
                Counter.builder("outbox.events.failed.count")
                        .description("Total number of outbox events failed")
                        .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${application.outbox.publish-delay:5000}")
    public void scheduledPublish() {
        this.publishEvents()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> log.debug("Published outbox event: {}", event.getId()),
                        ex -> log.error("Error occurred while publishing outbox events", ex));
    }

    @Transactional
    public Flux<OutboxEvent> publishEvents() {
        return outboxEventRepository
                .claimPendingEvents(100)
                .doOnNext(event -> log.info("Processing pending outbox event: {}", event.getId()))
                .flatMap(
                        event -> {
                            log.info("Sending outbox event to Kafka: {}", event.getId());
                            return catalogKafkaProducer
                                    .send(event.getPayload().toString())
                                    .flatMap(
                                            success -> {
                                                if (success) {
                                                    publishedEventCounter.increment();
                                                    return outboxEventRepository.save(
                                                            event.setStatus(
                                                                            OutboxEventStatus
                                                                                    .PUBLISHED)
                                                                    .setProcessedAt(
                                                                            OffsetDateTime.now()));
                                                } else {
                                                    failedEventCounter.increment();
                                                    return outboxEventRepository.save(
                                                            event.setStatus(
                                                                            OutboxEventStatus
                                                                                    .FAILED)
                                                                    .setErrorMessage(
                                                                            "Kafka send failed"));
                                                }
                                            })
                                    .onErrorResume(
                                            ex -> {
                                                log.error(
                                                        "Error publishing event {}",
                                                        event.getId(),
                                                        ex);
                                                failedEventCounter.increment();
                                                return outboxEventRepository.save(
                                                        event.setStatus(OutboxEventStatus.FAILED)
                                                                .setErrorMessage(ex.getMessage()));
                                            });
                        });
    }
}
