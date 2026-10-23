/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalogservice.common.AbstractIntegrationTest;
import com.example.catalogservice.entities.OutboxEvent;
import com.example.catalogservice.entities.OutboxEventStatus;
import com.example.catalogservice.entities.OutboxPayload;
import com.example.catalogservice.services.OutboxPublisher;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.test.StepVerifier;

// A fixed delay still allows an immediate first run that can claim the test fixtures.
@MockitoBean(types = OutboxPublisher.class)
class OutboxEventRepositoryIT extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll().block();
    }

    @Test
    void shouldRetainRecentFailuresRegardlessOfCreationTime() {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime cutoff = now.minusDays(30);
        OutboxEvent expired =
                event(OutboxEventStatus.FAILED, now).setProcessedAt(cutoff.minusDays(1));
        OutboxEvent recent = event(OutboxEventStatus.FAILED, now.minusDays(60)).setProcessedAt(now);
        OutboxEvent boundary =
                event(OutboxEventStatus.FAILED, now.minusDays(60)).setProcessedAt(cutoff);
        OutboxEvent unknownFailureTime = event(OutboxEventStatus.FAILED, now.minusDays(60));
        OutboxEvent published =
                event(OutboxEventStatus.PUBLISHED, now.minusDays(60))
                        .setProcessedAt(cutoff.minusDays(1));
        outboxEventRepository
                .saveAll(List.of(expired, recent, boundary, unknownFailureTime, published))
                .collectList()
                .block();

        StepVerifier.create(
                        outboxEventRepository.deleteAllByStatusAndProcessedAtBefore(
                                OutboxEventStatus.FAILED, cutoff))
                .expectNext(1)
                .verifyComplete();
        assertThat(outboxEventRepository.findAll().map(OutboxEvent::getId).collectList().block())
                .containsExactlyInAnyOrder(
                        recent.getId(),
                        boundary.getId(),
                        unknownFailureTime.getId(),
                        published.getId());
    }

    @Test
    void shouldRecordFailureTimeWhenReaperExhaustsRetries() {
        OffsetDateTime now = OffsetDateTime.now();
        OutboxEvent retry =
                event(OutboxEventStatus.PROCESSING, now.minusDays(60))
                        .setLockedAt(now.minusMinutes(10))
                        .setRetryCount(1);
        OutboxEvent exhausted =
                event(OutboxEventStatus.PROCESSING, now.minusDays(60))
                        .setLockedAt(now.minusMinutes(10))
                        .setRetryCount(2);
        OutboxEvent active =
                event(OutboxEventStatus.PROCESSING, now.minusDays(60))
                        .setLockedAt(now)
                        .setRetryCount(2);
        outboxEventRepository.saveAll(List.of(retry, exhausted, active)).collectList().block();

        StepVerifier.create(outboxEventRepository.reapOrphanedEvents(now.minusMinutes(5), 3))
                .expectNext(2L)
                .verifyComplete();
        OutboxEvent reaped = outboxEventRepository.findById(exhausted.getId()).block();
        assertThat(reaped.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(reaped.getProcessedAt()).isBetween(now, OffsetDateTime.now());
        assertThat(reaped.getRetryCount()).isEqualTo(3);
        assertThat(reaped.getLockedAt()).isNull();
        assertThat(reaped.getVersion()).isEqualTo(1);
        OutboxEvent pending = outboxEventRepository.findById(retry.getId()).block();
        assertThat(pending.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(pending.getProcessedAt()).isNull();
        assertThat(outboxEventRepository.findById(active.getId()).block().getStatus())
                .isEqualTo(OutboxEventStatus.PROCESSING);

        StepVerifier.create(
                        outboxEventRepository.deleteAllByStatusAndProcessedAtBefore(
                                OutboxEventStatus.FAILED, now.minusDays(30)))
                .expectNext(0)
                .verifyComplete();
    }

    private OutboxEvent event(OutboxEventStatus status, OffsetDateTime createdAt) {
        return new OutboxEvent()
                .setId(UUID.randomUUID())
                .setAggregateType("Product")
                .setAggregateId("P001")
                .setEventType("ProductCreated")
                .setPayload(new OutboxPayload("{}"))
                .setStatus(status)
                .setCreatedAt(createdAt)
                .setVersion(0)
                .setNew(true);
    }

    @Test
    void shouldClaimPendingEventsSuccessfully() {
        OutboxEvent event1 =
                new OutboxEvent()
                        .setId(UUID.randomUUID())
                        .setAggregateType("Product")
                        .setAggregateId("P001")
                        .setEventType("ProductCreated")
                        .setPayload(new OutboxPayload("{\"productCode\":\"P001\"}"))
                        .setStatus(OutboxEventStatus.PENDING)
                        .setCreatedAt(OffsetDateTime.now().minusMinutes(5))
                        .setRetryCount(0)
                        .setVersion(0)
                        .setNew(true);

        OutboxEvent event2 =
                new OutboxEvent()
                        .setId(UUID.randomUUID())
                        .setAggregateType("Product")
                        .setAggregateId("P002")
                        .setEventType("ProductCreated")
                        .setPayload(new OutboxPayload("{\"productCode\":\"P002\"}"))
                        .setStatus(OutboxEventStatus.PENDING)
                        .setCreatedAt(OffsetDateTime.now().minusMinutes(2))
                        .setRetryCount(0)
                        .setVersion(0)
                        .setNew(true);

        outboxEventRepository.saveAll(List.of(event1, event2)).collectList().block();

        StepVerifier.create(outboxEventRepository.claimPendingEvents(1))
                .assertNext(
                        claimed -> {
                            assertThat(claimed.getId()).isEqualTo(event1.getId());
                            assertThat(claimed.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
                            assertThat(claimed.getLockedAt()).isNotNull();
                            assertThat(claimed.getVersion()).isEqualTo(1);
                            assertThat(claimed.getPayload().content()).contains("P001");
                        })
                .verifyComplete();

        StepVerifier.create(outboxEventRepository.claimPendingEvents(1))
                .assertNext(
                        claimed -> {
                            assertThat(claimed.getId()).isEqualTo(event2.getId());
                            assertThat(claimed.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
                            assertThat(claimed.getLockedAt()).isNotNull();
                            assertThat(claimed.getVersion()).isEqualTo(1);
                            assertThat(claimed.getPayload().content()).contains("P002");
                        })
                .verifyComplete();

        StepVerifier.create(outboxEventRepository.claimPendingEvents(1)).verifyComplete();
    }

    @Test
    void shouldReturnEmptyFluxWhenLimitIsZeroOrNegative() {
        StepVerifier.create(outboxEventRepository.claimPendingEvents(0)).verifyComplete();
        StepVerifier.create(outboxEventRepository.claimPendingEvents(-1)).verifyComplete();
    }

    @Test
    void shouldDeleteAllByStatusAndCreatedAtBeforeAndReturnCount() {
        OffsetDateTime now = OffsetDateTime.now();
        OutboxEvent oldPublished1 =
                new OutboxEvent()
                        .setId(UUID.randomUUID())
                        .setAggregateType("Product")
                        .setAggregateId("P001")
                        .setEventType("ProductCreated")
                        .setPayload(new OutboxPayload("{\"productCode\":\"P001\"}"))
                        .setStatus(OutboxEventStatus.PUBLISHED)
                        .setCreatedAt(now.minusDays(10))
                        .setNew(true);

        OutboxEvent oldPublished2 =
                new OutboxEvent()
                        .setId(UUID.randomUUID())
                        .setAggregateType("Product")
                        .setAggregateId("P002")
                        .setEventType("ProductCreated")
                        .setPayload(new OutboxPayload("{\"productCode\":\"P002\"}"))
                        .setStatus(OutboxEventStatus.PUBLISHED)
                        .setCreatedAt(now.minusDays(8))
                        .setNew(true);

        OutboxEvent recentPublished =
                new OutboxEvent()
                        .setId(UUID.randomUUID())
                        .setAggregateType("Product")
                        .setAggregateId("P003")
                        .setEventType("ProductCreated")
                        .setPayload(new OutboxPayload("{\"productCode\":\"P003\"}"))
                        .setStatus(OutboxEventStatus.PUBLISHED)
                        .setCreatedAt(now.minusDays(2))
                        .setNew(true);

        OutboxEvent oldPending =
                new OutboxEvent()
                        .setId(UUID.randomUUID())
                        .setAggregateType("Product")
                        .setAggregateId("P004")
                        .setEventType("ProductCreated")
                        .setPayload(new OutboxPayload("{\"productCode\":\"P004\"}"))
                        .setStatus(OutboxEventStatus.PENDING)
                        .setCreatedAt(now.minusDays(10))
                        .setNew(true);

        outboxEventRepository
                .saveAll(List.of(oldPublished1, oldPublished2, recentPublished, oldPending))
                .collectList()
                .block();

        StepVerifier.create(
                        outboxEventRepository.deleteAllByStatusAndCreatedAtBefore(
                                OutboxEventStatus.PUBLISHED, now.minusDays(7)))
                .expectNext(2)
                .verifyComplete();

        StepVerifier.create(outboxEventRepository.count()).expectNext(2L).verifyComplete();
    }
}
