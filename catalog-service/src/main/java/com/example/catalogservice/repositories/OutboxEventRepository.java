/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.repositories;

import com.example.catalogservice.entities.OutboxEvent;
import com.example.catalogservice.entities.OutboxEventStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OutboxEventRepository extends ReactiveCrudRepository<OutboxEvent, UUID> {

    @Query(
            """
            UPDATE outbox_events
            SET status = 'PROCESSING', locked_at = NOW()
            WHERE id IN (
                SELECT id FROM outbox_events
                WHERE status = 'PENDING'
                ORDER BY created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING *
            """)
    Flux<OutboxEvent> claimPendingEvents(int limit);

    @Modifying
    @Query(
            """
            UPDATE outbox_events
            SET status = 'PENDING', locked_at = NULL, retry_count = retry_count + 1
            WHERE status = 'PROCESSING' AND locked_at < :threshold
            """)
    Mono<Long> reapOrphanedEvents(OffsetDateTime threshold);

    Mono<Long> countByStatus(OutboxEventStatus status);

    Mono<Void> deleteAllByStatusAndCreatedAtBefore(OutboxEventStatus status, OffsetDateTime time);
}
