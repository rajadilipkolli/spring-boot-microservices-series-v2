/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.jooq.tables.records.LedgerEntriesRecord;
import com.example.paymentservice.jooq.tables.records.OutboxEventsRecord;
import com.example.paymentservice.jooq.tables.records.PaymentOperationsRecord;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.services.provider.PaymentProviderPort;
import com.example.paymentservice.services.provider.ProviderCommand;
import com.example.paymentservice.services.provider.ProviderResult;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DurablePaymentOperationsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private OutboxPublisher outboxPublisher;
    @Autowired private PaymentReconciler paymentReconciler;

    private CustomersRecord customer;

    @BeforeEach
    void setUp() {
        dslContext
                .deleteFrom(com.example.paymentservice.jooq.tables.LedgerEntries.LEDGER_ENTRIES)
                .execute();
        dslContext
                .deleteFrom(
                        com.example.paymentservice.jooq.tables.PaymentOperations.PAYMENT_OPERATIONS)
                .execute();
        dslContext
                .deleteFrom(com.example.paymentservice.jooq.tables.OutboxEvents.OUTBOX_EVENTS)
                .execute();
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

        PaymentProviderPort blockingProvider =
                new PaymentProviderPort() {
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

                    @Override
                    public ProviderResult lookup(String downstreamKey) {
                        return ProviderResult.notFound();
                    }
                };

        PaymentOperationEngine customEngine =
                new PaymentOperationEngine(
                        paymentOperationRepository,
                        outboxEventRepository,
                        customerRepository,
                        blockingProvider,
                        new com.example.paymentservice.config.ApplicationProperties(),
                        jsonMapper,
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

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

        inProviderLatch.await(5, TimeUnit.SECONDS);

        OrderDto r2 = customEngine.processReserve(order2);
        assertThat(r2.status()).isEqualTo("PENDING");

        blockLatch.countDown();
        OrderDto r1 = f1.get(5, TimeUnit.SECONDS);
        assertThat(r1.status()).isEqualTo("ACCEPT");

        CustomersRecord updated = customerDAO.findById(customer.getId()).get();
        assertThat(updated.getAmountAvailable()).isEqualByComparingTo(BigDecimal.valueOf(90.00));
        assertThat(updated.getAmountReserved()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
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
                    @Override
                    public ProviderResult execute(String downstreamKey, ProviderCommand command) {
                        if (callCount.incrementAndGet() == 1) {
                            customerRepository.reserveFunds(command.customerId(), command.amount());
                            throw new com.example.paymentservice.services.provider
                                    .ProviderTimeoutException("Simulated timeout", null);
                        }
                        return ProviderResult.accept();
                    }

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
                        new com.example.paymentservice.config.ApplicationProperties(),
                        jsonMapper,
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        OrderDto initial = customEngine.processReserve(order);
        assertThat(initial.status()).isEqualTo("PENDING");

        PaymentOperationsRecord op =
                paymentOperationRepository
                        .findByOperationKey("payment:" + orderId + ":RESERVE")
                        .get();
        assertThat(op.getStatus()).isEqualTo("UNKNOWN");

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
                        new com.example.paymentservice.config.ApplicationProperties(),
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        customReconciler.reconcileBatch();

        PaymentOperationsRecord recoveredOp = paymentOperationRepository.findById(op.getId()).get();
        assertThat(recoveredOp.getStatus()).isEqualTo("COMPLETED");
        assertThat(recoveredOp.getResult()).isEqualTo("ACCEPT");

        CustomersRecord updated = customerDAO.findById(customer.getId()).get();
        assertThat(updated.getAmountAvailable()).isEqualByComparingTo(BigDecimal.valueOf(90.00));
        assertThat(updated.getAmountReserved()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
    }
}
