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
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OutboxEventRepository extends ReactiveCrudRepository<OutboxEvent, UUID> {

    @Query(
            """
            UPDATE outbox_events
            SET status = 'PROCESSING'
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

    Mono<Long> countByStatus(OutboxEventStatus status);

    Mono<Void> deleteAllByStatusAndCreatedAtBefore(OutboxEventStatus status, OffsetDateTime time);
}
