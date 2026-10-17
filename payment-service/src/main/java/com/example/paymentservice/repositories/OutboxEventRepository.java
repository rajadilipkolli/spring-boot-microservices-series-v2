/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository {

    OutboxEventsRecord insert(OutboxEventsRecord event);

    Optional<OutboxEventsRecord> findById(Long id);

    Optional<OutboxEventsRecord> findByAggregateIdAndEventType(
            String aggregateId, String eventType);

    List<OutboxEventsRecord> findAwaitingReserveReplies(String aggregateId);

    List<OutboxEventsRecord> claimPendingEvents(int limit, String lockedBy);

    boolean markPublished(Long id, int expectedVersion);

    boolean markFailed(Long id, int expectedVersion, String errorMessage);

    boolean returnToPending(Long id, int expectedVersion, String errorMessage);

    boolean promoteToPending(String aggregateId, String eventType, String payload);

    int reapStaleProcessing(OffsetDateTime threshold, int maxRetries);
}
