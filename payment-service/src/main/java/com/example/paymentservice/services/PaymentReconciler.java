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
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

    /**
     * Creates the reconciler with operation storage, completion handling, provider access,
     * configuration, and reconciliation counters.
     */
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

    /** Runs one reconciliation batch when enabled and no local reconciliation is active. */
    @Scheduled(fixedDelayString = "${application.reconciler.delay:1s}")
    @SchedulerLock(name = "payment_reconcile", lockAtMostFor = "1m", lockAtLeastFor = "500ms")
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

    /**
     * Claims one configured batch of received, expired, or due unknown operations and reconciles
     * each. This entry point runs even when scheduled reconciliation is disabled. Claim failures
     * and unhandled completion or backoff persistence failures propagate and stop the batch.
     */
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

    /**
     * Looks up a claimed operation before retrying execution with its persisted downstream key.
     * Known outcomes complete the operation; lookup failures or exhausted attempts defer it with
     * backoff. Execution and subsequent completion exceptions also trigger backoff; failures to
     * persist backoff propagate.
     */
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
            log.warn(
                    "Operation {} exhausted attempts while provider returned NOT_FOUND, keeping UNKNOWN with backoff",
                    op.getId());
            backoffOperation(op);
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

    /** Completes with the claim token, promoting awaiting replies only for reserve operations. */
    private void completeOperation(PaymentOperationsRecord op, long fencingToken, String outcome) {
        if ("RESERVE".equalsIgnoreCase(op.getOperationType())) {
            OrderDto dummyOrder =
                    new OrderDto(
                            op.getOrderId(), op.getCustomerId(), outcome, null, List.of(), null);
            operationEngine.executeCompletion(op.getId(), fencingToken, dummyOrder, outcome);
        } else {
            operationEngine.executeSettlementCompletion(op.getId(), fencingToken, outcome);
        }
    }

    /**
     * Rejects a reservation and readies its replies, or marks a settlement FAILED with no result.
     */
    private void markFailed(PaymentOperationsRecord op, long fencingToken) {
        if ("RESERVE".equalsIgnoreCase(op.getOperationType())) {
            OrderDto dummyOrder =
                    new OrderDto(
                            op.getOrderId(), op.getCustomerId(), "REJECT", null, List.of(), null);
            operationEngine.executeCompletion(op.getId(), fencingToken, dummyOrder, "REJECT");
        } else {
            operationRepository.complete(op.getId(), fencingToken, "FAILED", null);
        }
    }

    /**
     * Defers the matching token as UNKNOWN using initial backoff times two to the attempt count,
     * with the exponent capped at four and the duration capped at maximum backoff. Database
     * failures propagate.
     */
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
