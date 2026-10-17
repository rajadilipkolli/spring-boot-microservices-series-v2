/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.jooq.tables.records.PaymentOperationsRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOperationRepository {

    /**
     * Inserts an operation, ignoring unique-key conflicts, and assigns a missing TSID to the input.
     * Database failures other than ignored conflicts propagate.
     *
     * @return the stored operation matching the input operation key, or null if none exists
     */
    PaymentOperationsRecord insertOrGet(PaymentOperationsRecord record);

    /** Returns the operation matching the ID, or empty if absent. */
    Optional<PaymentOperationsRecord> findById(Long id);

    /** Returns the operation matching the stable operation key, or empty if absent. */
    Optional<PaymentOperationsRecord> findByOperationKey(String operationKey);

    /**
     * Returns the operation matching the order and operation type, or empty if absent.
     *
     * @throws org.jooq.exception.TooManyRowsException if more than one operation matches
     */
    Optional<PaymentOperationsRecord> findByOrderIdAndOperationType(
            Long orderId, String operationType);

    /**
     * Returns the operation matching the ID, or empty if absent, locking a matching row for update
     * until the transaction ends.
     */
    Optional<PaymentOperationsRecord> findByIdForUpdate(Long id);

    /**
     * Claims a RECEIVED or IN_FLIGHT operation with a matching token, setting its owner and lease
     * and incrementing its fencing token and attempt count. Does not check lease expiration.
     *
     * @param expectedFencingToken the token read before attempting the claim
     * @return false if the row is absent, its token differs, or its status is ineligible
     */
    boolean claim(Long id, long expectedFencingToken, String ownerId, OffsetDateTime leaseUntil);

    /**
     * Stores the supplied status and result only when the operation's fencing token matches.
     *
     * @return true if updated, false if absent or the token differs
     */
    boolean complete(Long id, long expectedFencingToken, String status, String result);

    /**
     * Stores the status and next reconciliation time only when the operation's fencing token
     * matches.
     *
     * @param nextReconcileAt the next eligible reconciliation time; null makes UNKNOWN operations
     *     immediately eligible
     * @return true if updated, false if absent or the token differs
     */
    boolean updateStatus(
            Long id, long expectedFencingToken, String status, OffsetDateTime nextReconcileAt);

    /**
     * Claims up to {@code limit} operations, selecting oldest first and skipping locked rows.
     * Eligible rows are RECEIVED, IN_FLIGHT with a lease strictly before now, or UNKNOWN with no
     * reconciliation time or a time at or before now. Sets IN_FLIGHT, owner, and lease, and
     * increments fencing tokens and attempt counts in one transaction.
     *
     * @return the claimed records with updated tokens, without a guaranteed result order
     */
    List<PaymentOperationsRecord> claimBatchForReconciliation(
            String ownerId, OffsetDateTime leaseUntil, int limit);
}
