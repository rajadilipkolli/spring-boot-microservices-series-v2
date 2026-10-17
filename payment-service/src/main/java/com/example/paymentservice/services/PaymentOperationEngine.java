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
import org.springframework.transaction.annotation.Transactional;
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

    private final Counter zombieRejectionsCounter;

    public PaymentOperationEngine(
            PaymentOperationRepository operationRepository,
            OutboxEventRepository outboxEventRepository,
            CustomerRepository customerRepository,
            PaymentProviderPort provider,
            ApplicationProperties properties,
            JsonMapper jsonMapper,
            MeterRegistry meterRegistry) {
        this.operationRepository = operationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.customerRepository = customerRepository;
        this.provider = provider;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.zombieRejectionsCounter =
                meterRegistry.counter("payment.operations.zombie.rejections");
    }

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

    @Transactional
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
            claimed = operationRepository.claim(locked.getId(), currentToken, ownerId, leaseUntil);
        } else if ("IN_FLIGHT".equals(locked.getStatus())
                && locked.getLeaseUntil() != null
                && locked.getLeaseUntil().isBefore(OffsetDateTime.now())) {
            claimed = operationRepository.claim(locked.getId(), currentToken, ownerId, leaseUntil);
        }

        PaymentOperationsRecord updatedRecord =
                operationRepository.findById(locked.getId()).orElse(locked);
        return new ArrivalResult(updatedRecord, false, claimed, null);
    }

    @Transactional
    public boolean executeCompletion(
            Long operationId, long fencingToken, OrderDto orderDto, String outcome) {
        boolean completed =
                operationRepository.complete(operationId, fencingToken, "COMPLETED", outcome);
        if (!completed) {
            return false;
        }

        OrderDto replyDto = orderDto.withStatus(outcome).withSource(AppConstants.SOURCE);
        String payloadJson = serialize(replyDto);

        if (orderDto.attemptId() != null) {
            String eventType = "RESERVE_REPLY:" + orderDto.attemptId();
            boolean promoted =
                    outboxEventRepository.promoteToPending(
                            String.valueOf(orderDto.orderId()), eventType, payloadJson);
            if (!promoted) {
                insertPendingReply(orderDto.orderId(), orderDto.attemptId(), outcome);
            }
        }
        return true;
    }

    @Transactional
    public boolean executeSettlementCompletion(
            Long operationId, long fencingToken, String outcome) {
        return operationRepository.complete(operationId, fencingToken, "COMPLETED", outcome);
    }

    @Transactional
    public void transitionToUnknown(Long operationId, long fencingToken) {
        OffsetDateTime nextReconcileAt =
                OffsetDateTime.now().plus(properties.getReconciler().getInitialBackoff());
        operationRepository.updateStatus(operationId, fencingToken, "UNKNOWN", nextReconcileAt);
    }

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

    private void validateCustomerExists(Long customerId) {
        if (customerId == null || customerRepository.findById(customerId).isEmpty()) {
            throw new CustomerNotFoundException(customerId);
        }
    }

    private BigDecimal calculateTotalOrderPrice(OrderDto orderDto) {
        return orderDto.items().stream()
                .map(OrderDto.OrderItemDto::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public String generateFingerprint(Long customerId, String operationType, BigDecimal amount) {
        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.HALF_UP);
        return customerId + ":" + operationType + ":" + normalizedAmount;
    }

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
