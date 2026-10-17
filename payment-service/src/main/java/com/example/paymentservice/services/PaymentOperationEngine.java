/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import com.example.paymentservice.config.ApplicationProperties;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.exception.OperationConflictException;
import com.example.paymentservice.exception.ReservationNotFoundException;
import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import com.example.paymentservice.jooq.tables.records.PaymentOperationsRecord;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.repositories.OutboxEventRepository;
import com.example.paymentservice.repositories.PaymentOperationRepository;
import com.example.paymentservice.services.provider.PaymentProviderPort;
import com.example.paymentservice.services.provider.ProviderCommand;
import com.example.paymentservice.services.provider.ProviderResult;
import com.example.paymentservice.services.provider.ProviderTimeoutException;
import com.example.paymentservice.utils.AppConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PaymentOperationEngine {

    private static final Logger log = LoggerFactory.getLogger(PaymentOperationEngine.class);

    private final PaymentOperationRepository operationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CustomerRepository customerRepository;
    private final PaymentProviderPort provider;
    private final ApplicationProperties properties;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    private final Counter zombieRejectionsCounter;

    /**
     * Creates the operation engine with persistence, provider execution, configuration, reply
     * serialization, metrics, and transaction management.
     */
    public PaymentOperationEngine(
            PaymentOperationRepository operationRepository,
            OutboxEventRepository outboxEventRepository,
            CustomerRepository customerRepository,
            PaymentProviderPort provider,
            ApplicationProperties properties,
            JsonMapper jsonMapper,
            MeterRegistry meterRegistry,
            PlatformTransactionManager transactionManager) {
        this.operationRepository = operationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.customerRepository = customerRepository;
        this.provider = provider;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.zombieRejectionsCounter =
                meterRegistry.counter("payment.operations.zombie.rejections");
    }

    /**
     * Reserves the sum of individually rounded line totals, reusing the operation for the order
     * across attempts. Non-null attempt IDs register outbox replies for eventual publication.
     * Provider exceptions return PENDING after attempting to mark the operation UNKNOWN. Arrival,
     * completion, status persistence, and reply serialization failures propagate.
     *
     * @return the order with source PAYMENT and its stored or provider outcome, or PENDING when
     *     unclaimed or provider execution fails; a provider outcome is returned even if fenced
     *     completion loses ownership
     * @throws CustomerNotFoundException if the customer ID is null or absent
     * @throws OperationConflictException if an existing operation has a different fingerprint
     * @throws NullPointerException if the order, items, an item, its price, or its quantity is null
     */
    public OrderDto processReserve(OrderDto orderDto) {
        validateCustomerExists(orderDto.customerId());
        BigDecimal amount = calculateTotalOrderPrice(orderDto);
        String operationKey = "payment:" + orderDto.orderId() + ":RESERVE";
        String fingerprint = generateFingerprint(orderDto.customerId(), "RESERVE", amount);

        String ownerId = UUID.randomUUID().toString();
        OffsetDateTime leaseUntil =
                OffsetDateTime.now().plus(properties.getOperation().getLeaseDuration());

        ArrivalResult arrival =
                executeArrival(
                        operationKey,
                        orderDto.orderId(),
                        orderDto.customerId(),
                        "RESERVE",
                        amount,
                        fingerprint,
                        orderDto.attemptId(),
                        ownerId,
                        leaseUntil);

        if (arrival.isTerminal()) {
            return orderDto.withStatus(arrival.terminalResult()).withSource(AppConstants.SOURCE);
        }

        if (!arrival.claimed()) {
            log.info(
                    "Operation {} already in flight or unknown, waiting for background completion",
                    operationKey);
            return orderDto.withStatus("PENDING").withSource(AppConstants.SOURCE);
        }

        PaymentOperationsRecord op = arrival.operation();
        long fencingToken = op.getFencingToken();
        String downstreamKey = op.getDownstreamKey();

        ProviderResult result;
        try {
            ProviderCommand cmd =
                    new ProviderCommand(
                            op.getId(),
                            op.getOrderId(),
                            op.getCustomerId(),
                            "RESERVE",
                            op.getAmount());
            result = provider.execute(downstreamKey, cmd);
        } catch (ProviderTimeoutException e) {
            log.warn("Provider timed out for operation {}, transitioning to UNKNOWN", op.getId());
            transitionToUnknown(op.getId(), fencingToken);
            return orderDto.withStatus("PENDING").withSource(AppConstants.SOURCE);
        } catch (Exception e) {
            log.warn(
                    "Provider execution transiently failed for operation {}, transitioning to UNKNOWN",
                    op.getId(),
                    e);
            transitionToUnknown(op.getId(), fencingToken);
            return orderDto.withStatus("PENDING").withSource(AppConstants.SOURCE);
        }

        boolean completed = executeCompletion(op.getId(), fencingToken, orderDto, result.outcome());
        if (!completed) {
            log.warn(
                    "Fenced completion lost for operation {} with fencing token {}",
                    op.getId(),
                    fencingToken);
            zombieRejectionsCounter.increment();
        }

        return orderDto.withStatus(result.outcome()).withSource(AppConstants.SOURCE);
    }

    /**
     * Settles using the completed reservation's stored amount, regardless of the incoming items.
     * Capture and release share one settlement key per order. A rejected reservation records a
     * NOT_APPLICABLE settlement without invoking the provider. Completed or unclaimed operations
     * return without execution; provider exceptions attempt to defer completion as UNKNOWN.
     * Arrival, completion, and status persistence failures propagate.
     *
     * @param settlementType CAPTURE to consume reserved funds or RELEASE to restore available funds
     * @throws CustomerNotFoundException if the customer ID is null or absent
     * @throws ReservationNotFoundException if the reservation is absent or not COMPLETED
     * @throws OperationConflictException if an applicable existing settlement's fingerprint differs
     */
    public void processSettlement(OrderDto orderDto, String settlementType) {
        validateCustomerExists(orderDto.customerId());
        String reserveOpKey = "payment:" + orderDto.orderId() + ":RESERVE";
        PaymentOperationsRecord reserveOp =
                operationRepository
                        .findByOperationKey(reserveOpKey)
                        .orElseThrow(() -> new ReservationNotFoundException(orderDto.orderId()));

        if (!"COMPLETED".equals(reserveOp.getStatus())) {
            throw new ReservationNotFoundException(orderDto.orderId());
        }

        if ("REJECT".equals(reserveOp.getResult())) {
            log.info(
                    "Reservation for order {} was REJECT, recording settlement as NOT_APPLICABLE",
                    orderDto.orderId());
            recordNotApplicableSettlement(orderDto, settlementType);
            return;
        }

        BigDecimal amount = reserveOp.getAmount();
        String operationKey = "payment:" + orderDto.orderId() + ":SETTLE";
        String fingerprint = generateFingerprint(orderDto.customerId(), settlementType, amount);

        String ownerId = UUID.randomUUID().toString();
        OffsetDateTime leaseUntil =
                OffsetDateTime.now().plus(properties.getOperation().getLeaseDuration());

        ArrivalResult arrival =
                executeArrival(
                        operationKey,
                        orderDto.orderId(),
                        orderDto.customerId(),
                        settlementType,
                        amount,
                        fingerprint,
                        orderDto.attemptId(),
                        ownerId,
                        leaseUntil);

        if (arrival.isTerminal() || !arrival.claimed()) {
            return;
        }

        PaymentOperationsRecord op = arrival.operation();
        long fencingToken = op.getFencingToken();
        String downstreamKey = op.getDownstreamKey();

        ProviderResult result;
        try {
            ProviderCommand cmd =
                    new ProviderCommand(
                            op.getId(),
                            op.getOrderId(),
                            op.getCustomerId(),
                            settlementType,
                            op.getAmount());
            result = provider.execute(downstreamKey, cmd);
        } catch (Exception e) {
            log.warn(
                    "Provider settlement failed or timed out for operation {}, transitioning to UNKNOWN",
                    op.getId(),
                    e);
            transitionToUnknown(op.getId(), fencingToken);
            return;
        }

        boolean completed = executeSettlementCompletion(op.getId(), fencingToken, result.outcome());
        if (!completed) {
            log.warn(
                    "Fenced completion lost for settlement operation {} with token {}",
                    op.getId(),
                    fencingToken);
            zombieRejectionsCounter.increment();
        }
    }

    /**
     * Registers an operation and any reserve reply in one transaction, replaying completed results
     * or claiming RECEIVED and expired IN_FLIGHT operations. UNKNOWN operations remain unclaimed.
     * Persistence and reply serialization failures propagate and roll back arrival changes.
     *
     * @param operationKey stable identity shared by retries; also determines the downstream key
     * @param fingerprint customer, operation type, and amount identity checked against stored input
     * @param attemptId identifies a reserve reply; null suppresses reply registration
     * @param ownerId owner assigned when a claim succeeds
     * @param leaseUntil absolute lease expiration for a successful claim
     * @return the stored operation, completion status, claim status, and any completed outcome
     * @throws OperationConflictException if the stored fingerprint differs
     * @throws java.util.NoSuchElementException if the inserted or existing operation cannot be
     *     locked
     */
    public ArrivalResult executeArrival(
            String operationKey,
            Long orderId,
            Long customerId,
            String operationType,
            BigDecimal amount,
            String fingerprint,
            String attemptId,
            String ownerId,
            OffsetDateTime leaseUntil) {
        return transactionTemplate.execute(
                status -> {
                    PaymentOperationsRecord record = new PaymentOperationsRecord();
                    record.setOperationKey(operationKey);
                    record.setOrderId(orderId);
                    record.setCustomerId(customerId);
                    record.setOperationType(operationType);
                    record.setAmount(amount);
                    record.setFingerprint(fingerprint);
                    record.setDownstreamKey("downstream:" + operationKey);
                    record.setStatus("RECEIVED");
                    record.setFencingToken(0L);

                    PaymentOperationsRecord persisted = operationRepository.insertOrGet(record);

                    PaymentOperationsRecord locked =
                            operationRepository.findByIdForUpdate(persisted.getId()).orElseThrow();

                    if (!Objects.equals(locked.getFingerprint(), fingerprint)) {
                        throw new OperationConflictException(
                                "Operation fingerprint mismatch for key: " + operationKey);
                    }

                    if ("COMPLETED".equals(locked.getStatus())) {
                        if (attemptId != null && "RESERVE".equals(operationType)) {
                            insertPendingReply(orderId, attemptId, locked.getResult());
                        }
                        return new ArrivalResult(locked, true, false, locked.getResult());
                    }

                    if (attemptId != null && "RESERVE".equals(operationType)) {
                        insertAwaitingReply(orderId, attemptId);
                    }

                    boolean claimed = false;
                    long currentToken = locked.getFencingToken();
                    if ("RECEIVED".equals(locked.getStatus())) {
                        claimed =
                                operationRepository.claim(
                                        locked.getId(), currentToken, ownerId, leaseUntil);
                    } else if ("IN_FLIGHT".equals(locked.getStatus())
                            && locked.getLeaseUntil() != null
                            && locked.getLeaseUntil().isBefore(OffsetDateTime.now())) {
                        claimed =
                                operationRepository.claim(
                                        locked.getId(), currentToken, ownerId, leaseUntil);
                    }

                    PaymentOperationsRecord updatedRecord =
                            operationRepository.findById(locked.getId()).orElse(locked);
                    return new ArrivalResult(updatedRecord, false, claimed, null);
                });
    }

    /**
     * Completes a reservation and makes all awaiting attempt replies publishable in one
     * transaction. Registers the supplied attempt's reply when non-null. Persistence and JSON
     * serialization failures propagate and roll back these changes.
     *
     * @param fencingToken the ownership token obtained when the operation was claimed
     * @param orderDto supplies the order identity and reply contents
     * @return false if the operation is absent or its token differs, leaving replies unchanged;
     *     true after completion and reply updates
     */
    public boolean executeCompletion(
            Long operationId, long fencingToken, OrderDto orderDto, String outcome) {
        return transactionTemplate.execute(
                status -> {
                    boolean completed =
                            operationRepository.complete(
                                    operationId, fencingToken, "COMPLETED", outcome);
                    if (!completed) {
                        return false;
                    }

                    for (OutboxEventsRecord reply :
                            outboxEventRepository.findAwaitingReserveReplies(
                                    String.valueOf(orderDto.orderId()))) {
                        String attemptId =
                                reply.getEventType().substring("RESERVE_REPLY:".length());
                        OrderDto replyDto =
                                new OrderDto(
                                        orderDto.orderId(),
                                        orderDto.customerId(),
                                        outcome,
                                        AppConstants.SOURCE,
                                        orderDto.items(),
                                        attemptId);
                        outboxEventRepository.promoteToPending(
                                reply.getAggregateId(), reply.getEventType(), serialize(replyDto));
                    }
                    if (orderDto.attemptId() != null) {
                        insertPendingReply(orderDto.orderId(), orderDto.attemptId(), outcome);
                    }
                    return true;
                });
    }

    /**
     * Stores a settlement outcome as COMPLETED only when its ownership token still matches.
     * Database failures propagate.
     *
     * @return true if updated, false if the operation is absent or its token differs
     */
    @Transactional
    public boolean executeSettlementCompletion(
            Long operationId, long fencingToken, String outcome) {
        return operationRepository.complete(operationId, fencingToken, "COMPLETED", outcome);
    }

    /**
     * Marks a matching ownership token UNKNOWN and schedules reconciliation after the initial
     * backoff. Missing operations and stale tokens leave state unchanged; database failures
     * propagate.
     */
    @Transactional
    public void transitionToUnknown(Long operationId, long fencingToken) {
        OffsetDateTime nextReconcileAt =
                OffsetDateTime.now().plus(properties.getReconciler().getInitialBackoff());
        operationRepository.updateStatus(operationId, fencingToken, "UNKNOWN", nextReconcileAt);
    }

    /**
     * Records a zero-amount, COMPLETED settlement with result NOT_APPLICABLE without changing
     * balances. Preserves any existing operation on a unique-key conflict without comparing its
     * fingerprint; other database failures propagate.
     */
    @Transactional
    public void recordNotApplicableSettlement(OrderDto orderDto, String settlementType) {
        String operationKey = "payment:" + orderDto.orderId() + ":SETTLE";
        String fingerprint =
                generateFingerprint(orderDto.customerId(), settlementType, BigDecimal.ZERO);
        PaymentOperationsRecord record = new PaymentOperationsRecord();
        record.setOperationKey(operationKey);
        record.setOrderId(orderDto.orderId());
        record.setCustomerId(orderDto.customerId());
        record.setOperationType(settlementType);
        record.setAmount(BigDecimal.ZERO);
        record.setFingerprint(fingerprint);
        record.setDownstreamKey("downstream:" + operationKey);
        record.setStatus("COMPLETED");
        record.setResult("NOT_APPLICABLE");
        record.setFencingToken(0L);
        operationRepository.insertOrGet(record);
    }

    /** Registers a payload-free reply awaiting the reserve result, preserving an existing reply. */
    private void insertAwaitingReply(Long orderId, String attemptId) {
        OutboxEventsRecord outbox = new OutboxEventsRecord();
        outbox.setAggregateType("PAYMENT_ORDER");
        outbox.setAggregateId(String.valueOf(orderId));
        outbox.setEventType("RESERVE_REPLY:" + attemptId);
        outbox.setStatus("AWAITING_RESULT");
        outbox.setTopic(AppConstants.PAYMENT_ORDERS_TOPIC);
        outbox.setMessageKey(String.valueOf(orderId));
        outbox.setPayload(null);
        outboxEventRepository.insert(outbox);
    }

    /**
     * Registers a publishable outcome reply with no customer or items. An existing reply for the
     * order and attempt is preserved; serialization and database failures propagate.
     */
    private void insertPendingReply(Long orderId, String attemptId, String outcome) {
        OrderDto replyDto =
                new OrderDto(
                        orderId,
                        null,
                        outcome,
                        AppConstants.SOURCE,
                        java.util.List.of(),
                        attemptId);
        String payloadJson = serialize(replyDto);

        OutboxEventsRecord outbox = new OutboxEventsRecord();
        outbox.setAggregateType("PAYMENT_ORDER");
        outbox.setAggregateId(String.valueOf(orderId));
        outbox.setEventType("RESERVE_REPLY:" + attemptId);
        outbox.setStatus("PENDING");
        outbox.setTopic(AppConstants.PAYMENT_ORDERS_TOPIC);
        outbox.setMessageKey(String.valueOf(orderId));
        outbox.setPayload(payloadJson);
        outboxEventRepository.insert(outbox);
    }

    /**
     * Checks that the customer exists; database lookup failures propagate.
     *
     * @throws CustomerNotFoundException if the ID is null or absent
     */
    private void validateCustomerExists(Long customerId) {
        if (customerId == null || customerRepository.findById(customerId).isEmpty()) {
            throw new CustomerNotFoundException(customerId);
        }
    }

    /**
     * Sums line totals individually rounded to two decimal places using HALF_UP.
     *
     * @return the total with scale two, or 0.00 for an empty item list
     * @throws NullPointerException if the order, items, an item, its price, or its quantity is null
     */
    private BigDecimal calculateTotalOrderPrice(OrderDto orderDto) {
        return orderDto.items().stream()
                .map(OrderDto.OrderItemDto::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Builds a colon-separated customer, operation type, and amount identity for replay checks. The
     * amount is rounded to scale two using HALF_UP; operation type casing is preserved.
     *
     * @throws NullPointerException if the amount is null
     */
    public String generateFingerprint(Long customerId, String operationType, BigDecimal amount) {
        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.HALF_UP);
        return customerId + ":" + operationType + ":" + normalizedAmount;
    }

    /**
     * Serializes a reply payload as JSON.
     *
     * @throws RuntimeException if serialization fails, retaining the original exception as its
     *     cause
     */
    private String serialize(Object obj) {
        try {
            return jsonMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    public record ArrivalResult(
            PaymentOperationsRecord operation,
            boolean isTerminal,
            boolean claimed,
            String terminalResult) {}
}
