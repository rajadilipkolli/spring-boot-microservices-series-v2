/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.repositories;

import com.example.catalogservice.entities.OutboxEvent;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public class OutboxEventRepositoryImpl implements CustomOutboxEventRepository {

    private static final String CLAIM_PENDING_EVENTS_SQL =
            """
            UPDATE outbox_events
            SET status = 'PROCESSING', locked_at = NOW(), version = version + 1
            WHERE id IN (
                SELECT id FROM outbox_events
                WHERE status = 'PENDING'
                ORDER BY created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING *
            """;

    private final DatabaseClient databaseClient;
    private final R2dbcConverter r2dbcConverter;

    public OutboxEventRepositoryImpl(DatabaseClient databaseClient, R2dbcConverter r2dbcConverter) {
        this.databaseClient = databaseClient;
        this.r2dbcConverter = r2dbcConverter;
    }

    @Override
    public Flux<OutboxEvent> claimPendingEvents(int limit) {
        if (limit <= 0) {
            return Flux.empty();
        }
        return databaseClient
                .sql(CLAIM_PENDING_EVENTS_SQL)
                .bind("limit", limit)
                .map((row, rowMetadata) -> r2dbcConverter.read(OutboxEvent.class, row, rowMetadata))
                .all();
    }
}
