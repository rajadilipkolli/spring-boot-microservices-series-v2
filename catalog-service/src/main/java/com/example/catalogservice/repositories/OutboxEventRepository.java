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
import reactor.core.publisher.Mono;

public interface OutboxEventRepository
        extends ReactiveCrudRepository<OutboxEvent, UUID>, CustomOutboxEventRepository {

    /**
     * Releases expired processing locks and increments retry counts and versions. Events whose new
     * retry count reaches or exceeds the limit are marked failed with an error message and terminal
     * processing time; the rest become pending again.
     *
     * @param threshold the exclusive cutoff for the lock timestamp
     * @param maxRetries the retry count at which an event is marked failed
     * @return the number of updated events; database failures are emitted as errors
     */
    @Modifying
    @Query(
            """
            UPDATE outbox_events
            SET status = CASE
                WHEN retry_count + 1 >= :maxRetries THEN 'FAILED'
                ELSE 'PENDING'
            END,
            processed_at = CASE
                WHEN retry_count + 1 >= :maxRetries THEN CURRENT_TIMESTAMP
                ELSE processed_at
            END,
            locked_at = NULL,
            retry_count = retry_count + 1,
            version = version + 1,
            error_message = CASE
                WHEN retry_count + 1 >= :maxRetries
                    THEN 'Exceeded max retries while reaping orphaned event'
                ELSE error_message
            END
            WHERE status = 'PROCESSING' AND locked_at < :threshold
            """)
    Mono<Long> reapOrphanedEvents(OffsetDateTime threshold, int maxRetries);

    Mono<Long> countByStatus(OutboxEventStatus status);

    Mono<Integer> deleteAllByStatusAndCreatedAtBefore(
            OutboxEventStatus status, OffsetDateTime time);

    /**
     * Deletes events with the supplied status and a terminal processing time strictly before the
     * cutoff. Events without a processing time are retained.
     *
     * @param status the status of events eligible for deletion
     * @param time the exclusive cutoff for terminal processing time
     * @return the number of deleted events; database failures are emitted as errors
     */
    Mono<Integer> deleteAllByStatusAndProcessedAtBefore(
            OutboxEventStatus status, OffsetDateTime time);
}
