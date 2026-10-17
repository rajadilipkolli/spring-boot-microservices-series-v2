/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.tables.PaymentOperations.PAYMENT_OPERATIONS;

import com.example.paymentservice.jooq.tables.records.PaymentOperationsRecord;
import io.hypersistence.tsid.TSID;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository implementation for managing {@link PaymentOperationsRecord} entities using jOOQ.
 *
 * <p>Note: jOOQ's {@code RecordListener} SPI only intercepts active-record CRUD operations (such as
 * {@code UpdatableRecord.insert()}). Because this repository uses fluent DSL queries with {@code
 * onDuplicateKeyIgnore()} (e.g. {@code dslContext.insertInto(...).set(record)...}), {@code
 * RecordListener} lifecycle events like {@code insertStart()} are not triggered. Therefore, missing
 * TSID primary keys must be assigned explicitly via {@link TSID.Factory} prior to statement
 * execution.
 */
@Repository
public class PaymentOperationRepositoryImpl implements PaymentOperationRepository {

    private final DSLContext dslContext;
    private final TSID.Factory tsidFactory;

    public PaymentOperationRepositoryImpl(DSLContext dslContext, TSID.Factory tsidFactory) {
        this.dslContext = dslContext;
        this.tsidFactory = tsidFactory;
    }

    @Override
    @Transactional
    public PaymentOperationsRecord insertOrGet(PaymentOperationsRecord record) {
        if (record.getId() == null) {
            record.setId(tsidFactory.generate().toLong());
        }
        dslContext.insertInto(PAYMENT_OPERATIONS).set(record).onDuplicateKeyIgnore().execute();

        return dslContext
                .selectFrom(PAYMENT_OPERATIONS)
                .where(PAYMENT_OPERATIONS.OPERATION_KEY.eq(record.getOperationKey()))
                .fetchOne();
    }

    @Override
    public Optional<PaymentOperationsRecord> findById(Long id) {
        return dslContext
                .selectFrom(PAYMENT_OPERATIONS)
                .where(PAYMENT_OPERATIONS.ID.eq(id))
                .fetchOptional();
    }

    @Override
    public Optional<PaymentOperationsRecord> findByOperationKey(String operationKey) {
        return dslContext
                .selectFrom(PAYMENT_OPERATIONS)
                .where(PAYMENT_OPERATIONS.OPERATION_KEY.eq(operationKey))
                .fetchOptional();
    }

    @Override
    public Optional<PaymentOperationsRecord> findByOrderIdAndOperationType(
            Long orderId, String operationType) {
        return dslContext
                .selectFrom(PAYMENT_OPERATIONS)
                .where(PAYMENT_OPERATIONS.ORDER_ID.eq(orderId))
                .and(PAYMENT_OPERATIONS.OPERATION_TYPE.eq(operationType))
                .fetchOptional();
    }

    @Override
    @Transactional
    public Optional<PaymentOperationsRecord> findByIdForUpdate(Long id) {
        return dslContext
                .selectFrom(PAYMENT_OPERATIONS)
                .where(PAYMENT_OPERATIONS.ID.eq(id))
                .forUpdate()
                .fetchOptional();
    }

    @Override
    @Transactional
    public boolean claim(
            Long id, long expectedFencingToken, String ownerId, OffsetDateTime leaseUntil) {
        int updated =
                dslContext
                        .update(PAYMENT_OPERATIONS)
                        .set(PAYMENT_OPERATIONS.STATUS, "IN_FLIGHT")
                        .set(PAYMENT_OPERATIONS.OWNER_ID, ownerId)
                        .set(PAYMENT_OPERATIONS.LEASE_UNTIL, leaseUntil)
                        .set(
                                PAYMENT_OPERATIONS.FENCING_TOKEN,
                                PAYMENT_OPERATIONS.FENCING_TOKEN.plus(1))
                        .set(
                                PAYMENT_OPERATIONS.ATTEMPT_COUNT,
                                PAYMENT_OPERATIONS.ATTEMPT_COUNT.plus(1))
                        .set(PAYMENT_OPERATIONS.UPDATED_AT, OffsetDateTime.now())
                        .where(PAYMENT_OPERATIONS.ID.eq(id))
                        .and(PAYMENT_OPERATIONS.FENCING_TOKEN.eq(expectedFencingToken))
                        .and(PAYMENT_OPERATIONS.STATUS.in("RECEIVED", "IN_FLIGHT"))
                        .execute();
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean complete(Long id, long expectedFencingToken, String status, String result) {
        int updated =
                dslContext
                        .update(PAYMENT_OPERATIONS)
                        .set(PAYMENT_OPERATIONS.STATUS, status)
                        .set(PAYMENT_OPERATIONS.RESULT, result)
                        .set(PAYMENT_OPERATIONS.UPDATED_AT, OffsetDateTime.now())
                        .where(PAYMENT_OPERATIONS.ID.eq(id))
                        .and(PAYMENT_OPERATIONS.FENCING_TOKEN.eq(expectedFencingToken))
                        .execute();
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean updateStatus(
            Long id, long expectedFencingToken, String status, OffsetDateTime nextReconcileAt) {
        int updated =
                dslContext
                        .update(PAYMENT_OPERATIONS)
                        .set(PAYMENT_OPERATIONS.STATUS, status)
                        .set(PAYMENT_OPERATIONS.NEXT_RECONCILE_AT, nextReconcileAt)
                        .set(PAYMENT_OPERATIONS.UPDATED_AT, OffsetDateTime.now())
                        .where(PAYMENT_OPERATIONS.ID.eq(id))
                        .and(PAYMENT_OPERATIONS.FENCING_TOKEN.eq(expectedFencingToken))
                        .execute();
        return updated > 0;
    }

    @Override
    @Transactional
    public List<PaymentOperationsRecord> claimBatchForReconciliation(
            String ownerId, OffsetDateTime leaseUntil, int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Long> claimedIds =
                dslContext
                        .select(PAYMENT_OPERATIONS.ID)
                        .from(PAYMENT_OPERATIONS)
                        .where(
                                PAYMENT_OPERATIONS
                                        .STATUS
                                        .eq("RECEIVED")
                                        .or(
                                                PAYMENT_OPERATIONS
                                                        .STATUS
                                                        .eq("IN_FLIGHT")
                                                        .and(
                                                                PAYMENT_OPERATIONS.LEASE_UNTIL.lt(
                                                                        now)))
                                        .or(
                                                PAYMENT_OPERATIONS
                                                        .STATUS
                                                        .eq("UNKNOWN")
                                                        .and(
                                                                PAYMENT_OPERATIONS
                                                                        .NEXT_RECONCILE_AT
                                                                        .isNull()
                                                                        .or(
                                                                                PAYMENT_OPERATIONS
                                                                                        .NEXT_RECONCILE_AT
                                                                                        .le(now)))))
                        .orderBy(PAYMENT_OPERATIONS.CREATED_AT.asc())
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                        .fetch(PAYMENT_OPERATIONS.ID);

        if (claimedIds.isEmpty()) {
            return List.of();
        }

        dslContext
                .update(PAYMENT_OPERATIONS)
                .set(PAYMENT_OPERATIONS.STATUS, "IN_FLIGHT")
                .set(PAYMENT_OPERATIONS.OWNER_ID, ownerId)
                .set(PAYMENT_OPERATIONS.LEASE_UNTIL, leaseUntil)
                .set(PAYMENT_OPERATIONS.FENCING_TOKEN, PAYMENT_OPERATIONS.FENCING_TOKEN.plus(1))
                .set(PAYMENT_OPERATIONS.ATTEMPT_COUNT, PAYMENT_OPERATIONS.ATTEMPT_COUNT.plus(1))
                .set(PAYMENT_OPERATIONS.UPDATED_AT, now)
                .where(PAYMENT_OPERATIONS.ID.in(claimedIds))
                .execute();

        return dslContext
                .selectFrom(PAYMENT_OPERATIONS)
                .where(PAYMENT_OPERATIONS.ID.in(claimedIds))
                .fetch();
    }
}
