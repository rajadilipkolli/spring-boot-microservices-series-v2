/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.paymentservice.config.ApplicationProperties;
import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

class OutboxPublisherTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final KafkaTemplate<String, OrderDto> kafkaTemplate = mock(KafkaTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final OutboxPublisher publisher =
            new OutboxPublisher(
                    repository, kafkaTemplate, new ApplicationProperties(), jsonMapper, meters);

    /** Stubs a two-event batch with successful Kafka sends and publication of the second event. */
    @BeforeEach
    void setUp() {
        when(repository.claimPendingEvents(anyInt(), anyString()))
                .thenReturn(List.of(event(1L), event(2L)));
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderDto.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(repository.markPublished(2L, 1)).thenReturn(true);
    }

    /**
     * Verifies that a status persistence failure after delivery requeues the event and increments
     * the publication mismatch counter.
     */
    @Test
    void statusUpdateFailureIsCountedSeparatelyAndRequeued() {
        when(repository.markPublished(1L, 1)).thenThrow(new IllegalStateException("status failed"));

        publisher.publishPendingEvents();

        verify(repository).returnToPending(1L, 1, "status failed");
        assertCounts(1, 0, 1);
    }

    /** Verifies that requeue failure after delivery allows subsequent events to publish. */
    @Test
    void failedRequeueAfterDeliveryDoesNotStopBatch() {
        when(repository.markPublished(1L, 1)).thenThrow(new IllegalStateException("status failed"));
        when(repository.returnToPending(1L, 1, "status failed"))
                .thenThrow(new IllegalStateException("requeue failed"));

        publisher.publishPendingEvents();

        verify(repository).markPublished(2L, 1);
        assertCounts(1, 0, 1);
    }

    /** Verifies that failure to persist a send failure allows subsequent events to publish. */
    @Test
    void failedFailureHandlingDoesNotStopBatch() {
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderDto.class)))
                .thenReturn(
                        CompletableFuture.failedFuture(new IllegalStateException("send failed")))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(repository.returnToPending(any(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("requeue failed"));

        publisher.publishPendingEvents();

        verify(repository).markPublished(2L, 1);
        assertCounts(1, 1, 0);
    }

    /**
     * Verifies that a publication version mismatch increments its counter without counting a send
     * failure.
     */
    @Test
    void versionMismatchIsCountedSeparately() {
        when(repository.markPublished(1L, 1)).thenReturn(false);

        publisher.publishPendingEvents();

        assertCounts(1, 0, 1);
    }

    /**
     * Creates a version-one outbox fixture with no retries and a serialized ACCEPT reply for the
     * supplied ID.
     */
    private OutboxEventsRecord event(Long id) {
        OutboxEventsRecord event = new OutboxEventsRecord();
        event.setId(id);
        event.setVersion(1);
        event.setRetryCount(0);
        event.setTopic("payment-orders");
        event.setMessageKey(id.toString());
        event.setPayload(
                jsonMapper.writeValueAsString(
                        new OrderDto(id, 1L, "ACCEPT", "PAYMENT", List.of(), "attempt-" + id)));
        return event;
    }

    /** Asserts the expected publication, send failure, and publication mismatch counter values. */
    private void assertCounts(double published, double failed, double mismatches) {
        assertThat(meters.counter("payment.outbox.events.published.count").count())
                .isEqualTo(published);
        assertThat(meters.counter("payment.outbox.events.failed.count").count()).isEqualTo(failed);
        assertThat(meters.counter("payment.outbox.events.publication.mismatch.count").count())
                .isEqualTo(mismatches);
    }
}
