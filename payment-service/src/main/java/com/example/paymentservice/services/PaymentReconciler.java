/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.config.ApplicationProperties;
import com.example.paymentservice.jooq.tables.records.PaymentOperationsRecord;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.repositories.PaymentOperationRepository;
import com.example.paymentservice.services.provider.PaymentProviderPort;
import com.example.paymentservice.services.provider.ProviderCommand;
import com.example.paymentservice.services.provider.ProviderResult;
import com.example.paymentservice.services.provider.ProviderTimeoutException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);

    private final PaymentOperationRepository operationRepository;
    private final PaymentOperationEngine operationEngine;
    private final PaymentProviderPort provider;
    private final ApplicationProperties properties;

    private final Counter reclaimedCounter;
    private final Counter completedCounter;
    private final Counter executedCounter;
    private final Counter failedCounter;
    private final AtomicBoolean isReconciling = new AtomicBoolean(false);

    public PaymentReconciler(
            PaymentOperationRepository operationRepository,
            PaymentOperationEngine operationEngine,
            PaymentProviderPort provider,
            ApplicationProperties properties,
            MeterRegistry meterRegistry) {
        this.operationRepository = operationRepository;
        this.operationEngine = operationEngine;
        this.provider = provider;
        this.properties = properties;

        this.reclaimedCounter = meterRegistry.counter("payment.reconciler.reclaimed.count");
        this.completedCounter = meterRegistry.counter("payment.reconciler.completed.count");
        this.executedCounter = meterRegistry.counter("payment.reconciler.executed.count");
        this.failedCounter = meterRegistry.counter("payment.reconciler.failed.count");
    }

    @Scheduled(fixedDelayString = "${application.reconciler.delay:1s}")
    public void scheduledReconcile() {
        if (!properties.getReconciler().isEnabled()) {
            return;
        }
        if (isReconciling.compareAndSet(false, true)) {
            try {
                reconcileBatch();
            } finally {
                isReconciling.set(false);
            }
        }
    }

    public void reconcileBatch() {
        String ownerId = UUID.randomUUID().toString();
        OffsetDateTime leaseUntil =
                OffsetDateTime.now().plus(properties.getOperation().getLeaseDuration());
        List<PaymentOperationsRecord> claimedOps =
                operationRepository.claimBatchForReconciliation(
                        ownerId, leaseUntil, properties.getReconciler().getBatchSize());

        for (PaymentOperationsRecord op : claimedOps) {
            reclaimedCounter.increment();
            reconcileOperation(op);
        }
    }

    private void reconcileOperation(PaymentOperationsRecord op) {
        String downstreamKey = op.getDownstreamKey();
        long fencingToken = op.getFencingToken();

        ProviderResult lookupResult;
        try {
            lookupResult = provider.lookup(downstreamKey);
        } catch (ProviderTimeoutException e) {
            log.warn(
                    "Lookup timed out for operation {}, returning to UNKNOWN with backoff",
                    op.getId());
            backoffOperation(op);
            return;
        } catch (Exception e) {
            log.warn(
                    "Lookup failed for operation {}, returning to UNKNOWN with backoff",
                    op.getId(),
                    e);
            backoffOperation(op);
            return;
        }

        if (lookupResult.status() == ProviderResult.Status.SUCCESS
                || lookupResult.status() == ProviderResult.Status.REJECTED) {
            log.info(
                    "Lookup succeeded for operation {}, completing with outcome {}",
                    op.getId(),
                    lookupResult.outcome());
            completeOperation(op, fencingToken, lookupResult.outcome());
            completedCounter.increment();
            return;
        }

        // Definitive NOT_FOUND
        if (op.getAttemptCount() >= properties.getOperation().getMaxAttempts()) {
            log.error(
                    "Operation {} exhausted attempts and definitive NOT_FOUND, marking FAILED",
                    op.getId());
            markFailed(op, fencingToken);
            failedCounter.increment();
            return;
        }

        // Execute with persisted downstream key
        try {
            ProviderCommand cmd =
                    new ProviderCommand(
                            op.getId(),
                            op.getOrderId(),
                            op.getCustomerId(),
                            op.getOperationType(),
                            op.getAmount());
            ProviderResult execResult = provider.execute(downstreamKey, cmd);
            executedCounter.increment();
            completeOperation(op, fencingToken, execResult.outcome());
        } catch (Exception e) {
            log.warn(
                    "Re-execution failed or timed out for operation {}, backing off to UNKNOWN",
                    op.getId(),
                    e);
            backoffOperation(op);
        }
    }

    private void completeOperation(PaymentOperationsRecord op, long fencingToken, String outcome) {
        if ("RESERVE".equalsIgnoreCase(op.getOperationType())) {
            OrderDto dummyOrder =
                    new OrderDto(
                            op.getOrderId(),
                            op.getCustomerId(),
                            outcome,
                            null,
                            List.of(),
                            "reconcile");
            operationEngine.executeCompletion(op.getId(), fencingToken, dummyOrder, outcome);
        } else {
            operationEngine.executeSettlementCompletion(op.getId(), fencingToken, outcome);
        }
    }

    private void markFailed(PaymentOperationsRecord op, long fencingToken) {
        if ("RESERVE".equalsIgnoreCase(op.getOperationType())) {
            OrderDto dummyOrder =
                    new OrderDto(
                            op.getOrderId(),
                            op.getCustomerId(),
                            "REJECT",
                            null,
                            List.of(),
                            "reconcile");
            operationEngine.executeCompletion(op.getId(), fencingToken, dummyOrder, "REJECT");
        } else {
            operationRepository.complete(op.getId(), fencingToken, "FAILED", null);
        }
    }

    private void backoffOperation(PaymentOperationsRecord op) {
        int attempt = op.getAttemptCount();
        Duration backoff =
                properties
                        .getReconciler()
                        .getInitialBackoff()
                        .multipliedBy((long) Math.pow(2, Math.min(attempt, 4)));
        if (backoff.compareTo(properties.getReconciler().getMaxBackoff()) > 0) {
            backoff = properties.getReconciler().getMaxBackoff();
        }
        OffsetDateTime nextReconcileAt = OffsetDateTime.now().plus(backoff);
        operationRepository.updateStatus(
                op.getId(), op.getFencingToken(), "UNKNOWN", nextReconcileAt);
    }
}
