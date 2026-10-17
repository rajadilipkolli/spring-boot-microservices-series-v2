/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository {

    /**
     * Inserts an event unless its aggregate ID and event type already exist. Assigns a missing TSID
     * to the input; other database failures propagate.
     *
     * @return the stored event for that aggregate ID and event type, preserving existing contents
     */
    OutboxEventsRecord insert(OutboxEventsRecord event);

    /** Returns the event matching the ID, or empty if absent. */
    Optional<OutboxEventsRecord> findById(Long id);

    /** Returns the event matching both aggregate ID and event type, or empty if absent. */
    Optional<OutboxEventsRecord> findByAggregateIdAndEventType(
            String aggregateId, String eventType);

    /** Returns AWAITING_RESULT events with a RESERVE_REPLY: prefix for the aggregate. */
    List<OutboxEventsRecord> findAwaitingReserveReplies(String aggregateId);

    /**
     * Claims oldest PENDING events, skipping locked rows. Marks them PROCESSING, records the
     * supplied owner and current lock time, and increments their versions in one transaction.
     *
     * @param limit maximum events to claim; nonpositive values return an empty list
     * @return the claimed events with updated versions, without a guaranteed result order
     */
    List<OutboxEventsRecord> claimPendingEvents(int limit, String lockedBy);

    /**
     * Marks the event PUBLISHED, clears its lock, and increments its version if the version
     * matches.
     *
     * @return true if updated, false if absent or the version differs
     */
    boolean markPublished(Long id, int expectedVersion);

    /**
     * Marks the event FAILED, stores the error, clears its lock, and increments its version if the
     * version matches. Leaves the retry count unchanged.
     *
     * @return true if updated, false if absent or the version differs
     */
    boolean markFailed(Long id, int expectedVersion, String errorMessage);

    /**
     * Requeues the event as PENDING, stores the error, clears its lock, and increments its retry
     * count and version if the version matches.
     *
     * @return true if updated, false if absent or the version differs
     */
    boolean returnToPending(Long id, int expectedVersion, String errorMessage);

    /**
     * Sets the matching AWAITING_RESULT event's payload and status to PENDING and increments its
     * version.
     *
     * @param payload the serialized reply ready for publication
     * @return true if promoted, false if no matching event is awaiting a result
     */
    boolean promoteToPending(String aggregateId, String eventType, String payload);

    /**
     * Clears locks on PROCESSING events locked strictly before the threshold, incrementing retry
     * counts and versions. Sets FAILED when the incremented retry count reaches {@code maxRetries};
     * otherwise sets PENDING.
     *
     * @return the number of reclaimed events
     */
    int reapStaleProcessing(OffsetDateTime threshold, int maxRetries);
}
