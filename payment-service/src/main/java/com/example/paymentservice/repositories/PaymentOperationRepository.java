/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.jooq.tables.records.PaymentOperationsRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOperationRepository {

    PaymentOperationsRecord insertOrGet(PaymentOperationsRecord record);

    Optional<PaymentOperationsRecord> findById(Long id);

    Optional<PaymentOperationsRecord> findByOperationKey(String operationKey);

    Optional<PaymentOperationsRecord> findByOrderIdAndOperationType(
            Long orderId, String operationType);

    Optional<PaymentOperationsRecord> findByIdForUpdate(Long id);

    boolean claim(Long id, long expectedFencingToken, String ownerId, OffsetDateTime leaseUntil);

    boolean complete(Long id, long expectedFencingToken, String status, String result);

    boolean updateStatus(
            Long id, long expectedFencingToken, String status, OffsetDateTime nextReconcileAt);

    List<PaymentOperationsRecord> claimBatchForReconciliation(
            String ownerId, OffsetDateTime leaseUntil, int limit);
}
