/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.tables.OutboxEvents.OUTBOX_EVENTS;

import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import io.hypersistence.tsid.TSID;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository implementation for managing {@link OutboxEventsRecord} entities using jOOQ.
 *
 * <p>Note: jOOQ's {@code RecordListener} SPI only intercepts active-record CRUD operations (such as
 * {@code UpdatableRecord.insert()}). Because this repository uses fluent DSL queries with {@code
 * onConflict(...).doNothing()} (e.g. {@code dslContext.insertInto(...).set(event)...}), {@code
 * RecordListener} lifecycle events like {@code insertStart()} are not triggered. Therefore, missing
 * TSID primary keys must be assigned explicitly via {@link TSID.Factory} prior to statement
 * execution.
 */
@Repository
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final DSLContext dslContext;
    private final TSID.Factory tsidFactory;

    public OutboxEventRepositoryImpl(DSLContext dslContext, TSID.Factory tsidFactory) {
        this.dslContext = dslContext;
        this.tsidFactory = tsidFactory;
    }

    @Override
    @Transactional
    public OutboxEventsRecord insert(OutboxEventsRecord event) {
        if (event.getId() == null) {
            event.setId(tsidFactory.generate().toLong());
        }
        dslContext
                .insertInto(OUTBOX_EVENTS)
                .set(event)
                .onConflict(OUTBOX_EVENTS.AGGREGATE_ID, OUTBOX_EVENTS.EVENT_TYPE)
                .doNothing()
                .execute();

        return dslContext
                .selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.AGGREGATE_ID.eq(event.getAggregateId()))
                .and(OUTBOX_EVENTS.EVENT_TYPE.eq(event.getEventType()))
                .fetchOne();
    }

    @Override
    public Optional<OutboxEventsRecord> findById(Long id) {
        return dslContext.selectFrom(OUTBOX_EVENTS).where(OUTBOX_EVENTS.ID.eq(id)).fetchOptional();
    }

    @Override
    public Optional<OutboxEventsRecord> findByAggregateIdAndEventType(
            String aggregateId, String eventType) {
        return dslContext
                .selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.AGGREGATE_ID.eq(aggregateId))
                .and(OUTBOX_EVENTS.EVENT_TYPE.eq(eventType))
                .fetchOptional();
    }

    @Override
    @Transactional
    public List<OutboxEventsRecord> claimPendingEvents(int limit, String lockedBy) {
        if (limit <= 0) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<Long> claimedIds =
                dslContext
                        .select(OUTBOX_EVENTS.ID)
                        .from(OUTBOX_EVENTS)
                        .where(OUTBOX_EVENTS.STATUS.eq("PENDING"))
                        .orderBy(OUTBOX_EVENTS.CREATED_AT.asc())
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                        .fetch(OUTBOX_EVENTS.ID);

        if (claimedIds.isEmpty()) {
            return List.of();
        }

        dslContext
                .update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.STATUS, "PROCESSING")
                .set(OUTBOX_EVENTS.LOCKED_AT, now)
                .set(OUTBOX_EVENTS.LOCKED_BY, lockedBy)
                .set(OUTBOX_EVENTS.VERSION, OUTBOX_EVENTS.VERSION.plus(1))
                .set(OUTBOX_EVENTS.UPDATED_AT, now)
                .where(OUTBOX_EVENTS.ID.in(claimedIds))
                .execute();

        return dslContext.selectFrom(OUTBOX_EVENTS).where(OUTBOX_EVENTS.ID.in(claimedIds)).fetch();
    }

    @Override
    @Transactional
    public boolean markPublished(Long id, int expectedVersion) {
        int updated =
                dslContext
                        .update(OUTBOX_EVENTS)
                        .set(OUTBOX_EVENTS.STATUS, "PUBLISHED")
                        .set(OUTBOX_EVENTS.LOCKED_AT, (OffsetDateTime) null)
                        .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                        .set(OUTBOX_EVENTS.VERSION, OUTBOX_EVENTS.VERSION.plus(1))
                        .set(OUTBOX_EVENTS.UPDATED_AT, OffsetDateTime.now())
                        .where(OUTBOX_EVENTS.ID.eq(id))
                        .and(OUTBOX_EVENTS.VERSION.eq(expectedVersion))
                        .execute();
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean markFailed(Long id, int expectedVersion, String errorMessage) {
        int updated =
                dslContext
                        .update(OUTBOX_EVENTS)
                        .set(OUTBOX_EVENTS.STATUS, "FAILED")
                        .set(OUTBOX_EVENTS.ERROR_MESSAGE, errorMessage)
                        .set(OUTBOX_EVENTS.LOCKED_AT, (OffsetDateTime) null)
                        .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                        .set(OUTBOX_EVENTS.VERSION, OUTBOX_EVENTS.VERSION.plus(1))
                        .set(OUTBOX_EVENTS.UPDATED_AT, OffsetDateTime.now())
                        .where(OUTBOX_EVENTS.ID.eq(id))
                        .and(OUTBOX_EVENTS.VERSION.eq(expectedVersion))
                        .execute();
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean returnToPending(Long id, int expectedVersion, String errorMessage) {
        int updated =
                dslContext
                        .update(OUTBOX_EVENTS)
                        .set(OUTBOX_EVENTS.STATUS, "PENDING")
                        .set(OUTBOX_EVENTS.RETRY_COUNT, OUTBOX_EVENTS.RETRY_COUNT.plus(1))
                        .set(OUTBOX_EVENTS.ERROR_MESSAGE, errorMessage)
                        .set(OUTBOX_EVENTS.LOCKED_AT, (OffsetDateTime) null)
                        .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                        .set(OUTBOX_EVENTS.VERSION, OUTBOX_EVENTS.VERSION.plus(1))
                        .set(OUTBOX_EVENTS.UPDATED_AT, OffsetDateTime.now())
                        .where(OUTBOX_EVENTS.ID.eq(id))
                        .and(OUTBOX_EVENTS.VERSION.eq(expectedVersion))
                        .execute();
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean promoteToPending(String aggregateId, String eventType, String payload) {
        int updated =
                dslContext
                        .update(OUTBOX_EVENTS)
                        .set(OUTBOX_EVENTS.STATUS, "PENDING")
                        .set(OUTBOX_EVENTS.PAYLOAD, payload)
                        .set(OUTBOX_EVENTS.VERSION, OUTBOX_EVENTS.VERSION.plus(1))
                        .set(OUTBOX_EVENTS.UPDATED_AT, OffsetDateTime.now())
                        .where(OUTBOX_EVENTS.AGGREGATE_ID.eq(aggregateId))
                        .and(OUTBOX_EVENTS.EVENT_TYPE.eq(eventType))
                        .and(OUTBOX_EVENTS.STATUS.eq("AWAITING_RESULT"))
                        .execute();
        return updated > 0;
    }

    @Override
    @Transactional
    public int reapStaleProcessing(OffsetDateTime threshold, int maxRetries) {
        return dslContext
                .update(OUTBOX_EVENTS)
                .set(
                        OUTBOX_EVENTS.STATUS,
                        DSL.when(OUTBOX_EVENTS.RETRY_COUNT.plus(1).ge(maxRetries), "FAILED")
                                .otherwise("PENDING"))
                .set(
                        OUTBOX_EVENTS.ERROR_MESSAGE,
                        DSL.when(
                                        OUTBOX_EVENTS.RETRY_COUNT.plus(1).ge(maxRetries),
                                        "Exceeded max retries while reaping orphaned event")
                                .otherwise(OUTBOX_EVENTS.ERROR_MESSAGE))
                .set(OUTBOX_EVENTS.RETRY_COUNT, OUTBOX_EVENTS.RETRY_COUNT.plus(1))
                .set(OUTBOX_EVENTS.VERSION, OUTBOX_EVENTS.VERSION.plus(1))
                .set(OUTBOX_EVENTS.LOCKED_AT, (OffsetDateTime) null)
                .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                .set(OUTBOX_EVENTS.UPDATED_AT, OffsetDateTime.now())
                .where(OUTBOX_EVENTS.STATUS.eq("PROCESSING"))
                .and(OUTBOX_EVENTS.LOCKED_AT.lt(threshold))
                .execute();
    }
}
