/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.example.common.dto.OrderDto;
import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.config.ApplicationProperties;
import com.example.paymentservice.jooq.tables.LedgerEntries;
import com.example.paymentservice.jooq.tables.OutboxEvents;
import com.example.paymentservice.jooq.tables.PaymentOperations;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.jooq.tables.records.LedgerEntriesRecord;
import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import com.example.paymentservice.jooq.tables.records.PaymentOperationsRecord;
import com.example.paymentservice.services.provider.PaymentProviderPort;
import com.example.paymentservice.services.provider.ProviderCommand;
import com.example.paymentservice.services.provider.ProviderResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.datafaker.Faker;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DurablePaymentOperationsIntegrationTest extends AbstractIntegrationTest {

    private CustomersRecord customer;

    /** Clears payment state and creates a customer with 100 available and no reserved funds. */
    @BeforeEach
    void setUp() {
        dslContext.deleteFrom(LedgerEntries.LEDGER_ENTRIES).execute();
        dslContext.deleteFrom(PaymentOperations.PAYMENT_OPERATIONS).execute();
        dslContext.deleteFrom(OutboxEvents.OUTBOX_EVENTS).execute();
        customerRepository.deleteAll();
        Faker faker = new Faker();
        CustomersRecord record = new CustomersRecord();
        record.setName("User_" + faker.number().randomNumber());
        record.setEmail("email_" + faker.number().randomNumber() + "@example.com");
        record.setPhone("1234567890");
        record.setAddressLine1("Street 1");
        record.setCity("City");
        record.setState("State");
        record.setZipCode("12345");
        record.setCountry("Country");
        record.setAmountAvailable(BigDecimal.valueOf(100.00));
        record.setAmountReserved(BigDecimal.ZERO);
        customer = customerRepository.saveCustomer(record);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-10.00"})
    void negativeReserveTotalIsRejectedWithoutSideEffects(BigDecimal amount) {
        OrderDto order =
                new OrderDto(
                        1014L,
                        customer.getId(),
                        "NEW",
                        "INVENTORY",
                        List.of(new OrderDto.OrderItemDto(1L, "p1", 1, amount)),
                        "negative-total");

        assertThatThrownBy(() -> paymentOperationEngine.processReserve(order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order total must not be negative");

        assertThat(dslContext.fetchCount(PaymentOperations.PAYMENT_OPERATIONS)).isZero();
        assertThat(dslContext.fetchCount(LedgerEntries.LEDGER_ENTRIES)).isZero();
        assertThat(dslContext.fetchCount(OutboxEvents.OUTBOX_EVENTS)).isZero();
        CustomersRecord unchanged = customerDAO.findById(customer.getId()).orElseThrow();
        assertThat(unchanged.getAmountAvailable()).isEqualByComparingTo("100");
        assertThat(unchanged.getAmountReserved()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "10.00"})
    void nonNegativeReserveTotalIsAccepted(BigDecimal amount) {
        OrderDto order =
                new OrderDto(
                        1015L,
                        customer.getId(),
                        "NEW",
                        "INVENTORY",
                        List.of(new OrderDto.OrderItemDto(1L, "p1", 1, amount)),
                        "non-negative-total");

        assertThat(paymentOperationEngine.processReserve(order).status()).isEqualTo("ACCEPT");

        PaymentOperationsRecord operation =
                paymentOperationRepository.findByOperationKey("payment:1015:RESERVE").orElseThrow();
        assertThat(operation.getAmount()).isEqualByComparingTo(amount);
        CustomersRecord updated = customerDAO.findById(customer.getId()).orElseThrow();
        assertThat(updated.getAmountAvailable())
                .isEqualByComparingTo(new BigDecimal("100").subtract(amount));
        assertThat(updated.getAmountReserved()).isEqualByComparingTo(amount);
        assertReplyReady(order.orderId(), order.attemptId(), "ACCEPT");
    }

    /**
     * Scenario 1: Simultaneous NEW submissions from two threads. Verify one ledger entry, one
     * reservation-sized balance change, and one outcome for each attemptId.
     */
    @Test
    void testSimultaneousNewSubmissionsFromTwoThreads() throws Exception {
        Long orderId = 1001L;
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "p1", 1, BigDecimal.TEN);

        OrderDto order1 =
                new OrderDto(
                        orderId,
                        customer.getId(),
                        "NEW",
                        "INVENTORY",
                        List.of(item),
                        "att-thread-1");
        OrderDto order2 =
                new OrderDto(
                        orderId,
                        customer.getId(),
                        "NEW",
                        "INVENTORY",
                        List.of(item),
                        "att-thread-2");

        CompletableFuture<OrderDto> f1 =
                CompletableFuture.supplyAsync(() -> paymentOperationEngine.processReserve(order1));
        CompletableFuture<OrderDto> f2 =
                CompletableFuture.supplyAsync(() -> paymentOperationEngine.processReserve(order2));

        OrderDto r1 = f1.get(10, TimeUnit.SECONDS);
        OrderDto r2 = f2.get(10, TimeUnit.SECONDS);

        assertThat(r1.status()).isIn("ACCEPT", "PENDING");
        assertThat(r2.status()).isIn("ACCEPT", "PENDING");

        outboxPublisher.publishPendingEvents();

        CustomersRecord updated = customerDAO.findById(customer.getId()).get();
        assertThat(updated.getAmountAvailable()).isEqualByComparingTo(BigDecimal.valueOf(90.00));
        assertThat(updated.getAmountReserved()).isEqualByComparingTo(BigDecimal.valueOf(10.00));

        Optional<LedgerEntriesRecord> ledger =
                ledgerRepository.findByDownstreamKey("downstream:payment:" + orderId + ":RESERVE");
        assertThat(ledger).isPresent();
        assertThat(ledger.get().getStatus()).isEqualTo("ACCEPT");

        Optional<OutboxEventsRecord> reply1 =
                outboxEventRepository.findByAggregateIdAndEventType(
                        String.valueOf(orderId), "RESERVE_REPLY:att-thread-1");
        Optional<OutboxEventsRecord> reply2 =
                outboxEventRepository.findByAggregateIdAndEventType(
                        String.valueOf(orderId), "RESERVE_REPLY:att-thread-2");
        assertThat(reply1).isPresent();
        assertThat(reply2).isPresent();
        assertReplyReady(orderId, "att-thread-1", "ACCEPT");
        assertReplyReady(orderId, "att-thread-2", "ACCEPT");
    }

    /**
     * Scenario 2: In-flight retry with a new attemptId using a blocking provider adapter. Verify no
     * second effect and ACCEPT for both attempts after release.
     */
    @Test
    void testInFlightRetryWithNewAttemptId() throws Exception {
        Long orderId = 1002L;
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "p1", 1, BigDecimal.TEN);

        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch inProviderLatch = new CountDownLatch(1);

        PaymentOperationEngine customEngine =
                getPaymentOperationEngine(inProviderLatch, blockLatch);

        OrderDto order1 =
                new OrderDto(
                        orderId,
                        customer.getId(),
                        "NEW",
                        "INVENTORY",
                        List.of(item),
                        "att-block-1");
        OrderDto order2 =
                new OrderDto(
                        orderId,
                        customer.getId(),
                        "NEW",
                        "INVENTORY",
                        List.of(item),
                        "att-block-2");

        CompletableFuture<OrderDto> f1 =
                CompletableFuture.supplyAsync(() -> customEngine.processReserve(order1));

        assertThat(inProviderLatch.await(5, TimeUnit.SECONDS)).isTrue();

        OrderDto r2 = customEngine.processReserve(order2);
        assertThat(r2.status()).isEqualTo("PENDING");

        blockLatch.countDown();
        OrderDto r1 = f1.get(5, TimeUnit.SECONDS);
        assertThat(r1.status()).isEqualTo("ACCEPT");
        assertReplyReady(orderId, "att-block-1", "ACCEPT");
        assertReplyReady(orderId, "att-block-2", "ACCEPT");

        CustomersRecord updated = customerDAO.findById(customer.getId()).get();
        assertThat(updated.getAmountAvailable()).isEqualByComparingTo(BigDecimal.valueOf(90.00));
        assertThat(updated.getAmountReserved()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
    }

    /**
     * Creates a test engine whose provider signals entry and waits on a latch before reserving
     * funds.
     */
    private @NonNull PaymentOperationEngine getPaymentOperationEngine(
            CountDownLatch inProviderLatch, CountDownLatch blockLatch) {
        PaymentProviderPort blockingProvider =
                new PaymentProviderPort() {
                    /**
                     * Signals provider entry, waits for release, reserves funds, and returns
                     * ACCEPT.
                     */
                    @Override
                    public ProviderResult execute(String downstreamKey, ProviderCommand command) {
                        inProviderLatch.countDown();
                        try {
                            blockLatch.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                        }
                        customerRepository.reserveFunds(command.customerId(), command.amount());
                        return ProviderResult.accept();
                    }

                    /** Returns NOT_FOUND for this blocking test provider. */
                    @Override
                    public ProviderResult lookup(String downstreamKey) {
                        return ProviderResult.notFound();
                    }
                };

        return new PaymentOperationEngine(
                paymentOperationRepository,
                outboxEventRepository,
                customerRepository,
                blockingProvider,
                new ApplicationProperties(),
                jsonMapper,
                new SimpleMeterRegistry(),
                transactionManager);
    }

    /**
     * Scenario 3: Expired lease and reconciliation before stale-owner completion. Verify zero rows
     * affected by the stale completion, one effect, and one published outcome.
     */
    @Test
    void testExpiredLeaseAndReconciliationBeforeStaleOwnerCompletion() {
        Long orderId = 1003L;
        String opKey = "payment:" + orderId + ":RESERVE";
        String fingerprint =
                paymentOperationEngine.generateFingerprint(
                        customer.getId(), "RESERVE", BigDecimal.TEN);

        PaymentOperationsRecord record = new PaymentOperationsRecord();
        record.setOperationKey(opKey);
        record.setOrderId(orderId);
        record.setCustomerId(customer.getId());
        record.setOperationType("RESERVE");
        record.setAmount(BigDecimal.TEN);
        record.setFingerprint(fingerprint);
        record.setDownstreamKey("downstream:" + opKey);
        record.setStatus("IN_FLIGHT");
        record.setFencingToken(1L);
        record.setLeaseUntil(OffsetDateTime.now().minusSeconds(5)); // expired
        record.setOwnerId("stale-owner");
        PaymentOperationsRecord inserted = paymentOperationRepository.insertOrGet(record);

        paymentReconciler.reconcileBatch();

        PaymentOperationsRecord reloaded =
                paymentOperationRepository.findById(inserted.getId()).get();
        assertThat(reloaded.getStatus()).isEqualTo("COMPLETED");
        assertThat(reloaded.getFencingToken()).isGreaterThan(1L);

        OrderDto staleOrder =
                new OrderDto(orderId, customer.getId(), "NEW", "INVENTORY", List.of(), "stale-att");
        boolean staleSuccess =
                paymentOperationEngine.executeCompletion(
                        inserted.getId(), 1L, staleOrder, "ACCEPT");
        assertThat(staleSuccess).isFalse(); // Zombie rejected

        CustomersRecord updated = customerDAO.findById(customer.getId()).get();
        assertThat(updated.getAmountAvailable()).isEqualByComparingTo(BigDecimal.valueOf(90.00));
        assertThat(updated.getAmountReserved()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
    }

    /** Scenario 4: Timeout to UNKNOWN and lookup-based recovery without a second effect. */
    @Test
    void testTimeoutToUnknownAndLookupRecovery() {
        Long orderId = 1004L;
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "p1", 1, BigDecimal.TEN);
        OrderDto order =
                new OrderDto(
                        orderId,
                        customer.getId(),
                        "NEW",
                        "INVENTORY",
                        List.of(item),
                        "att-timeout");

        AtomicInteger callCount = new AtomicInteger(0);

        PaymentProviderPort delayedProvider =
                new PaymentProviderPort() {
                    /**
                     * Simulates a timeout after the first balance reservation and returns ACCEPT on
                     * later calls.
                     */
                    @Override
                    public ProviderResult execute(String downstreamKey, ProviderCommand command) {
                        if (callCount.incrementAndGet() == 1) {
                            customerRepository.reserveFunds(command.customerId(), command.amount());
                            throw new com.example.paymentservice.services.provider
                                    .ProviderTimeoutException("Simulated timeout", null);
                        }
                        return ProviderResult.accept();
                    }

                    /** Returns ACCEPT to simulate recovery of the timed-out payment. */
                    @Override
                    public ProviderResult lookup(String downstreamKey) {
                        return ProviderResult.accept();
                    }
                };

        PaymentOperationEngine customEngine =
                new PaymentOperationEngine(
                        paymentOperationRepository,
                        outboxEventRepository,
                        customerRepository,
                        delayedProvider,
                        new ApplicationProperties(),
                        jsonMapper,
                        new SimpleMeterRegistry(),
                        transactionManager);

        OrderDto initial = customEngine.processReserve(order);
        assertThat(initial.status()).isEqualTo("PENDING");

        PaymentOperationsRecord op =
                paymentOperationRepository
                        .findByOperationKey("payment:" + orderId + ":RESERVE")
                        .get();
        assertThat(op.getStatus()).isEqualTo("UNKNOWN");

        OrderDto retry =
                new OrderDto(
                        orderId,
                        customer.getId(),
                        "NEW",
                        "INVENTORY",
                        List.of(item),
                        "att-timeout-2");
        assertThat(customEngine.processReserve(retry).status()).isEqualTo("PENDING");

        // Make UNKNOWN operation due immediately for reconciliation in test
        dslContext
                .update(com.example.paymentservice.jooq.tables.PaymentOperations.PAYMENT_OPERATIONS)
                .set(
                        com.example.paymentservice.jooq.tables.PaymentOperations.PAYMENT_OPERATIONS
                                .NEXT_RECONCILE_AT,
                        java.time.OffsetDateTime.now().minusSeconds(1))
                .where(
                        com.example.paymentservice.jooq.tables.PaymentOperations.PAYMENT_OPERATIONS
                                .ID.eq(op.getId()))
                .execute();

        PaymentReconciler customReconciler =
                new PaymentReconciler(
                        paymentOperationRepository,
                        customEngine,
                        delayedProvider,
                        new ApplicationProperties(),
                        new SimpleMeterRegistry());

        customReconciler.reconcileBatch();

        PaymentOperationsRecord recoveredOp = paymentOperationRepository.findById(op.getId()).get();
        assertThat(recoveredOp.getStatus()).isEqualTo("COMPLETED");
        assertThat(recoveredOp.getResult()).isEqualTo("ACCEPT");
        assertReplyReady(orderId, "att-timeout", "ACCEPT");
        assertReplyReady(orderId, "att-timeout-2", "ACCEPT");
        assertThat(
                        outboxEventRepository.findByAggregateIdAndEventType(
                                String.valueOf(orderId), "RESERVE_REPLY:reconcile"))
                .isEmpty();

        CustomersRecord updated = customerDAO.findById(customer.getId()).get();
        assertThat(updated.getAmountAvailable()).isEqualByComparingTo(BigDecimal.valueOf(90.00));
        assertThat(updated.getAmountReserved()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
    }

    /**
     * Verifies that reply insertion failure rolls back operation arrival without changing customer
     * funds.
     */
    @Test
    void arrivalRollsBackWhenReplyInsertFails() {
        OrderDto order = order(1010L, "arrival-failure");
        doThrow(new IllegalStateException("reply insert failed"))
                .when(outboxRepositorySpy)
                .insert(any());

        assertThatThrownBy(() -> paymentOperationEngine.processReserve(order))
                .isInstanceOf(IllegalStateException.class);

        assertThat(paymentOperationRepository.findByOperationKey("payment:1010:RESERVE")).isEmpty();
        assertThat(customerDAO.findById(customer.getId()).orElseThrow().getAmountAvailable())
                .isEqualByComparingTo("100");
    }

    /**
     * Verifies that reply promotion failure rolls back completion and leaves the operation in
     * flight with an awaiting reply.
     */
    @Test
    void completionRollsBackWhenReplyPromotionFails() {
        OrderDto order = order(1011L, "completion-failure");
        doThrow(new IllegalStateException("reply promotion failed"))
                .when(outboxRepositorySpy)
                .promoteToPending(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> paymentOperationEngine.processReserve(order))
                .isInstanceOf(IllegalStateException.class);

        PaymentOperationsRecord operation =
                paymentOperationRepository.findByOperationKey("payment:1011:RESERVE").orElseThrow();
        assertThat(operation.getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(operation.getResult()).isNull();
        assertThat(
                        outboxEventRepository
                                .findByAggregateIdAndEventType(
                                        "1011", "RESERVE_REPLY:completion-failure")
                                .orElseThrow()
                                .getStatus())
                .isEqualTo("AWAITING_RESULT");
    }

    /**
     * Verifies that direct claims cannot reopen completed or unknown operations even with a
     * matching token.
     */
    @Test
    void claimCannotReopenCompletedOrUnknownOperation() {
        paymentOperationEngine.processReserve(order(1012L, "terminal"));
        PaymentOperationsRecord operation =
                paymentOperationRepository.findByOperationKey("payment:1012:RESERVE").orElseThrow();
        assertThat(
                        paymentOperationRepository.claim(
                                operation.getId(),
                                operation.getFencingToken(),
                                "new-owner",
                                OffsetDateTime.now().plusSeconds(10)))
                .isFalse();
        assertThat(paymentOperationRepository.findById(operation.getId()).orElseThrow().getStatus())
                .isEqualTo("COMPLETED");

        paymentOperationRepository.updateStatus(
                operation.getId(), operation.getFencingToken(), "UNKNOWN", OffsetDateTime.now());
        assertThat(
                        paymentOperationRepository.claim(
                                operation.getId(),
                                operation.getFencingToken(),
                                "new-owner",
                                OffsetDateTime.now().plusSeconds(10)))
                .isFalse();
    }

    /**
     * Verifies that concurrent executions for one downstream key wait for the first transaction and
     * replay its result with one balance mutation.
     */
    @Test
    void concurrentLedgerExecutionsWaitAndReplayCommittedResult() throws Exception {
        Long orderId = 1013L;
        PaymentOperationEngine.ArrivalResult arrival =
                paymentOperationEngine.executeArrival(
                        "payment:" + orderId + ":RESERVE",
                        orderId,
                        customer.getId(),
                        "RESERVE",
                        BigDecimal.TEN,
                        paymentOperationEngine.generateFingerprint(
                                customer.getId(), "RESERVE", BigDecimal.TEN),
                        "ledger",
                        "owner",
                        OffsetDateTime.now().plusSeconds(15));
        PaymentOperationsRecord operation = arrival.operation();
        ProviderCommand command =
                new ProviderCommand(
                        operation.getId(), orderId, customer.getId(), "RESERVE", BigDecimal.TEN);
        CountDownLatch firstMutated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger mutations = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            Object result = invocation.callRealMethod();
                            mutations.incrementAndGet();
                            firstMutated.countDown();
                            assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
                            return result;
                        })
                .when(customerRepository)
                .reserveFunds(customer.getId(), BigDecimal.TEN);

        CompletableFuture<ProviderResult> first =
                CompletableFuture.supplyAsync(
                        () -> ledgerAdapter.execute(operation.getDownstreamKey(), command));
        CompletableFuture<ProviderResult> second = null;
        try {
            assertThat(firstMutated.await(5, TimeUnit.SECONDS)).isTrue();
            second =
                    CompletableFuture.supplyAsync(
                            () -> ledgerAdapter.execute(operation.getDownstreamKey(), command));
            await().atMost(Duration.ofSeconds(1))
                    .untilAsserted(
                            () ->
                                    assertThat(
                                                    dslContext.fetchCount(
                                                            dslContext
                                                                    .selectFrom("pg_locks")
                                                                    .where(
                                                                            "locktype = 'advisory' and not granted")))
                                            .isPositive());
        } finally {
            releaseFirst.countDown();
        }
        assertThat(first.get(5, TimeUnit.SECONDS).outcome()).isEqualTo("ACCEPT");
        assertThat(second).isNotNull();
        assertThat(second.get(5, TimeUnit.SECONDS).outcome()).isEqualTo("ACCEPT");
        assertThat(mutations).hasValue(1);
        assertThat(customerDAO.findById(customer.getId()).orElseThrow().getAmountAvailable())
                .isEqualByComparingTo("90");
    }

    /**
     * Creates a new test order for the fixture customer with one ten-unit item and the supplied
     * attempt ID.
     */
    private OrderDto order(Long orderId, String attemptId) {
        return new OrderDto(
                orderId,
                customer.getId(),
                "NEW",
                "INVENTORY",
                List.of(new OrderDto.OrderItemDto(1L, "p1", 1, BigDecimal.TEN)),
                attemptId);
    }

    /**
     * Asserts that the attempt reply is pending or published and contains the expected attempt ID
     * and outcome.
     */
    private void assertReplyReady(Long orderId, String attemptId, String outcome) {
        OutboxEventsRecord reply =
                outboxEventRepository
                        .findByAggregateIdAndEventType(
                                String.valueOf(orderId), "RESERVE_REPLY:" + attemptId)
                        .orElseThrow();
        assertThat(reply.getStatus()).isIn("PENDING", "PUBLISHED");
        OrderDto payload = jsonMapper.readValue(reply.getPayload(), OrderDto.class);
        assertThat(payload.attemptId()).isEqualTo(attemptId);
        assertThat(payload.status()).isEqualTo(outcome);
    }
}
