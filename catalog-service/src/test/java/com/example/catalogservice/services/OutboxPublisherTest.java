/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.catalogservice.config.ApplicationProperties;
import com.example.catalogservice.entities.OutboxEvent;
import com.example.catalogservice.entities.OutboxEventStatus;
import com.example.catalogservice.entities.OutboxPayload;
import com.example.catalogservice.kafka.CatalogKafkaProducer;
import com.example.catalogservice.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OutboxPublisherTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recordsFailureTimeOnlyWhenRetriesAreExhausted(boolean sendThrows) {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        CatalogKafkaProducer producer = mock(CatalogKafkaProducer.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApplicationProperties.Outbox outbox = new ApplicationProperties.Outbox();
        outbox.setMaxRetries(1);
        OutboxPublisher publisher =
                new OutboxPublisher(
                        repository,
                        producer,
                        new ApplicationProperties("http://localhost:8082", null, null, outbox),
                        registry);
        OutboxEvent event =
                new OutboxEvent()
                        .setId(UUID.randomUUID())
                        .setAggregateId("P001")
                        .setPayload(new OutboxPayload("{}"))
                        .setStatus(OutboxEventStatus.PROCESSING)
                        .setCreatedAt(OffsetDateTime.now().minusDays(60));
        when(repository.claimPendingEvents(100)).thenReturn(Flux.just(event));
        when(repository.save(any(OutboxEvent.class)))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(producer.send("P001", "{}"))
                .thenReturn(
                        sendThrows
                                ? Mono.error(new IllegalStateException("Kafka unavailable"))
                                : Mono.just(false));

        StepVerifier.create(publisher.publishEvents())
                .assertNext(
                        saved -> {
                            assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
                            assertThat(saved.getRetryCount()).isEqualTo(1);
                            assertThat(saved.getProcessedAt()).isNull();
                        })
                .verifyComplete();

        OffsetDateTime beforeFailure = OffsetDateTime.now();
        StepVerifier.create(publisher.publishEvents())
                .assertNext(
                        saved -> {
                            assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
                            assertThat(saved.getProcessedAt())
                                    .isBetween(beforeFailure, OffsetDateTime.now());
                            assertThat(saved.getErrorMessage())
                                    .isEqualTo(
                                            sendThrows
                                                    ? "Kafka unavailable"
                                                    : "Kafka send returned false");
                        })
                .verifyComplete();
        assertThat(registry.get("outbox.events.failed.count").counter().count()).isEqualTo(1);
    }
}
