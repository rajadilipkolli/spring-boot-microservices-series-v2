/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.catalogservice.config.ApplicationProperties;
import com.example.catalogservice.entities.OutboxEvent;
import com.example.catalogservice.entities.OutboxEventStatus;
import com.example.catalogservice.kafka.CatalogKafkaProducer;
import com.example.catalogservice.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class OutboxScheduledJobsTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void publishWaitsForCompletionAndAllowsTheNextRun() throws Exception {
        OutboxPublisher self = mock(OutboxPublisher.class);
        Sinks.Many<OutboxEvent> work = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch subscribed = new CountDownLatch(1);
        when(self.claimEvents())
                .thenReturn(work.asFlux().doOnSubscribe(ignored -> subscribed.countDown()));
        OutboxPublisher publisher =
                new OutboxPublisher(
                        repository,
                        mock(CatalogKafkaProducer.class),
                        mock(ApplicationProperties.class),
                        registry,
                        self);

        assertWaits(publisher::scheduledPublish, subscribed, () -> work.tryEmitComplete());
        when(self.claimEvents()).thenReturn(Flux.empty());
        publisher.scheduledPublish();
        verify(self, times(2)).claimEvents();
    }

    @Test
    void reaperWaitsForCompletion() throws Exception {
        ApplicationProperties properties = mock(ApplicationProperties.class, RETURNS_DEEP_STUBS);
        when(properties.outbox().getLockTimeout()).thenReturn(Duration.ofMinutes(5));
        Sinks.One<Long> work = Sinks.one();
        CountDownLatch subscribed = new CountDownLatch(1);
        when(repository.reapOrphanedEvents(any(), anyInt()))
                .thenReturn(work.asMono().doOnSubscribe(ignored -> subscribed.countDown()));
        OutboxPublisher publisher =
                new OutboxPublisher(
                        repository, mock(CatalogKafkaProducer.class), properties, registry, null);
        assertWaits(publisher::scheduledReap, subscribed, () -> work.tryEmitValue(1L));
    }

    @Test
    void metricsWaitForTheLastQuery() throws Exception {
        Sinks.One<Long> work = Sinks.one();
        CountDownLatch subscribed = new CountDownLatch(1);
        when(repository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(Mono.just(1L));
        when(repository.countByStatus(OutboxEventStatus.PUBLISHED)).thenReturn(Mono.just(2L));
        when(repository.countByStatus(OutboxEventStatus.FAILED))
                .thenReturn(work.asMono().doOnSubscribe(ignored -> subscribed.countDown()));
        OutboxHousekeepingService housekeeping =
                new OutboxHousekeepingService(repository, registry);
        assertWaits(housekeeping::updateMetrics, subscribed, () -> work.tryEmitValue(3L));
        assertThat(registry.get("outbox.events.failed").gauge().value()).isEqualTo(3);
    }

    @Test
    void cleanupWaitsForDeletion() throws Exception {
        Sinks.One<Integer> work = Sinks.one();
        CountDownLatch subscribed = new CountDownLatch(1);
        when(repository.deleteAllByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(work.asMono().doOnSubscribe(ignored -> subscribed.countDown()));
        OutboxHousekeepingService housekeeping =
                new OutboxHousekeepingService(repository, registry);
        assertWaits(housekeeping::cleanupPublishedEvents, subscribed, () -> work.tryEmitValue(2));
    }

    private void assertWaits(Runnable job, CountDownLatch subscribed, Runnable complete)
            throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(job);
            try {
                assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> future.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                complete.run();
            }
            future.get(5, TimeUnit.SECONDS);
        }
    }
}
