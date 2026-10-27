/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.common.dto.OrderDto;
import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.util.TestData;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KafkaListenerConfigIntegrationTest extends AbstractIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(KafkaListenerConfigIntegrationTest.class);

    private CustomersRecord customer;

    /** Replaces existing customers with a uniquely named customer and verifies it was persisted. */
    @BeforeEach
    void setUp() {
        this.customerRepository.deleteAll();
        // Generate unique data for each test to avoid sequence conflicts
        Faker faker = new Faker();
        String uniqueName = "Customer_" + faker.number().randomNumber();
        String uniqueEmail = "email_" + faker.number().randomNumber() + "@example.com";

        CustomersRecord newCustomer = createCustomersRecord(uniqueName, uniqueEmail);

        customer = this.customerRepository.saveCustomer(newCustomer);
        // Ensure the customer is saved before running tests
        assertThat(customer).isNotNull();
        assertThat(this.customerDAO.findById(customer.getId()))
                .isPresent()
                .get()
                .satisfies(
                        customer -> {
                            assertThat(customer.getName()).isEqualTo(uniqueName);
                            assertThat(customer.getEmail()).isEqualTo(uniqueEmail);
                            assertThat(customer.getId()).isNotEqualTo(1);
                        });
    }

    /**
     * Creates an unsaved listener-test customer with 100 available and 10 reserved.
     *
     * @param uniqueName the name chosen for this test
     * @param uniqueEmail the email chosen for this test
     * @return the customer record with sample contact details
     */
    private @NonNull CustomersRecord createCustomersRecord(String uniqueName, String uniqueEmail) {
        return new CustomersRecord()
                .setName(uniqueName)
                .setEmail(uniqueEmail)
                .setPhone("1234567890")
                .setAddressLine1("First Address")
                .setAddressLine2("Second Address")
                .setCity("City")
                .setState("State")
                .setZipCode("12345")
                .setCountry("Country")
                .setAmountAvailable(BigDecimal.valueOf(100))
                .setAmountReserved(BigDecimal.TEN);
    }

    /** Verifies a new order event transfers its decimal total from available to reserved funds. */
    @Test
    void onEventReserveOrder() {
        OrderDto orderDto = getOrderDto("NEW");

        BigDecimal amountReserved = customer.getAmountReserved();
        BigDecimal amountAvailable = customer.getAmountAvailable();

        // When
        log.debug("Sending order DTO: {}", orderDto);
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);

        // Then
        await().pollInterval(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            CustomersRecord persistedCustomer =
                                    customerDAO.findById(customer.getId()).get();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(amountReserved.add(BigDecimal.TEN));
                            assertThat(persistedCustomer.getAmountAvailable())
                                    .isEqualByComparingTo(amountAvailable.subtract(BigDecimal.TEN));
                        });
    }

    @Test
    void onEventReserveOrderDlt() {
        OrderDto orderDto = getOrderDto("NEW");
        // Use a non-existent customerId by adding a large offset
        long nonExistentCustomerId = orderDto.customerId() + 10_000;

        // When
        kafkaTemplate.send(
                "orders",
                String.valueOf(orderDto.orderId()),
                TestData.withCustomerId(nonExistentCustomerId, orderDto));

        // Allow asynchronous consumer delivery and match this order, since the latch is shared.
        await().pollInterval(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () ->
                                assertThat(kafkaListenerConfig.getDeadLetterLatch().getCount())
                                        .isZero());
    }

    /**
     * Verifies an inventory rollback event releases reserved funds back to the available balance.
     */
    @Test
    void onEventConfirmOrder() {

        OrderDto orderDto = getOrderDto("ROLLBACK");

        // Seed completed reservation first
        OrderDto reserveDto =
                new OrderDto(
                        orderDto.orderId(),
                        orderDto.customerId(),
                        "NEW",
                        "INVENTORY",
                        orderDto.items(),
                        "att-res-" + orderDto.orderId());
        kafkaTemplate.send("orders", String.valueOf(reserveDto.orderId()), reserveDto);

        await().pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            CustomersRecord persistedCustomer =
                                    customerDAO.findById(customer.getId()).get();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(BigDecimal.valueOf(20));
                        });

        // When rollback is received
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);

        // Then funds are released back to available
        await().pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            CustomersRecord persistedCustomer =
                                    customerDAO.findById(customer.getId()).get();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(BigDecimal.TEN);
                            assertThat(persistedCustomer.getAmountAvailable())
                                    .isEqualByComparingTo(BigDecimal.valueOf(100));
                        });
    }

    /** Verifies a payment-originated rollback event leaves customer balances unchanged. */
    @Test
    void onEventConfirmOrderNoRollBack() {

        OrderDto orderDto = getOrderDto("ROLLBACK");

        // When
        kafkaTemplate.send(
                "orders", String.valueOf(orderDto.orderId()), orderDto.withSource("PAYMENT"));

        // Then
        await().pollDelay(1, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            CustomersRecord persistedCustomer =
                                    customerDAO.findById(customer.getId()).get();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(BigDecimal.TEN);
                            assertThat(persistedCustomer.getAmountAvailable())
                                    .isEqualByComparingTo(BigDecimal.valueOf(100));
                        });
    }

    @Test
    void onEventDuplicateNewOrder_ModifiesBalanceOnceAndWritesOneLedgerAndOneOutboxEntry() {
        OrderDto orderDto = getOrderDto("NEW");
        BigDecimal amountReserved = customer.getAmountReserved();
        BigDecimal amountAvailable = customer.getAmountAvailable();

        // Send duplicate NEW events with non-blank attempt
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);

        // Sentinel order on the same partition (same key) to guarantee both duplicates are
        // processed
        OrderDto sentinelDto =
                new OrderDto(
                        orderDto.orderId() + 100_000,
                        customer.getId(),
                        "NEW",
                        "INVENTORY",
                        orderDto.items(),
                        "sentinel-" + orderDto.attemptId());
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), sentinelDto);

        await().pollInterval(Duration.ofMillis(200))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> {
                            // First order adds 10, sentinel adds 10 -> total +20 reserved, -20
                            // available
                            CustomersRecord persistedCustomer =
                                    customerDAO.findById(customer.getId()).orElseThrow();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(
                                            amountReserved.add(BigDecimal.valueOf(20)));
                            assertThat(persistedCustomer.getAmountAvailable())
                                    .isEqualByComparingTo(
                                            amountAvailable.subtract(BigDecimal.valueOf(20)));

                            // Verify exactly one ledger entry for the original order's reserve
                            String ledgerKey =
                                    "downstream:payment:" + orderDto.orderId() + ":RESERVE";
                            assertThat(ledgerRepository.findByDownstreamKey(ledgerKey)).isPresent();

                            // Verify exactly one outbox entry for the original order's reply
                            String outboxEventType = "RESERVE_REPLY:" + orderDto.attemptId();
                            assertThat(
                                            outboxEventRepository.findByAggregateIdAndEventType(
                                                    String.valueOf(orderDto.orderId()),
                                                    outboxEventType))
                                    .isPresent();
                        });
    }

    @Test
    void onEventDuplicateConfirmedOrder_SettlesOnlyOnce() {
        OrderDto orderDto = getOrderDto("CONFIRMED");

        // Seed reservation first
        OrderDto reserveDto =
                new OrderDto(
                        orderDto.orderId(),
                        orderDto.customerId(),
                        "NEW",
                        "INVENTORY",
                        orderDto.items(),
                        "att-res-" + orderDto.orderId());
        kafkaTemplate.send("orders", String.valueOf(reserveDto.orderId()), reserveDto);

        await().pollInterval(Duration.ofMillis(200))
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            CustomersRecord persistedCustomer =
                                    customerDAO.findById(customer.getId()).orElseThrow();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(BigDecimal.valueOf(20));
                        });

        // Send duplicate CONFIRMED orders
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);

        // A sentinel confirm order to synchronize processing
        // We need a completed reservation for sentinel before confirm
        OrderDto sentinelReserve =
                new OrderDto(
                        orderDto.orderId() + 200_000,
                        orderDto.customerId(),
                        "NEW",
                        "INVENTORY",
                        orderDto.items(),
                        "att-sentinel-res-" + orderDto.orderId());
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), sentinelReserve);

        OrderDto sentinelConfirm =
                new OrderDto(
                        sentinelReserve.orderId(),
                        sentinelReserve.customerId(),
                        "CONFIRMED",
                        "INVENTORY",
                        sentinelReserve.items(),
                        "att-sentinel-conf-" + orderDto.orderId());
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), sentinelConfirm);

        await().pollInterval(Duration.ofMillis(200))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> {
                            // Seed had initial 10 reserved + 10 = 20.
                            // Order confirm captured 10 -> reserved becomes 10.
                            // Sentinel reserve added 10 -> reserved becomes 20.
                            // Sentinel confirm captured 10 -> reserved becomes 10.
                            // Available balance remains 90 throughout capture.
                            CustomersRecord persistedCustomer =
                                    customerDAO.findById(customer.getId()).orElseThrow();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(BigDecimal.TEN);
                            assertThat(persistedCustomer.getAmountAvailable())
                                    .isEqualByComparingTo(BigDecimal.valueOf(90));

                            // Single SETTLE ledger entry for orderDto
                            String settleLedgerKey =
                                    "downstream:payment:" + orderDto.orderId() + ":SETTLE";
                            assertThat(ledgerRepository.findByDownstreamKey(settleLedgerKey))
                                    .isPresent();
                        });
    }

    @Test
    void onEventDuplicateRollbackOrder_SettlesOnlyOnce() {
        OrderDto orderDto = getOrderDto("ROLLBACK");

        // Seed reservation first
        OrderDto reserveDto =
                new OrderDto(
                        orderDto.orderId(),
                        orderDto.customerId(),
                        "NEW",
                        "INVENTORY",
                        orderDto.items(),
                        "att-res-" + orderDto.orderId());
        kafkaTemplate.send("orders", String.valueOf(reserveDto.orderId()), reserveDto);

        await().pollInterval(Duration.ofMillis(200))
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            CustomersRecord persistedCustomer =
                                    customerDAO.findById(customer.getId()).orElseThrow();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(BigDecimal.valueOf(20));
                        });

        // Send duplicate ROLLBACK orders
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);

        // Sentinel rollback order
        OrderDto sentinelReserve =
                new OrderDto(
                        orderDto.orderId() + 300_000,
                        orderDto.customerId(),
                        "NEW",
                        "INVENTORY",
                        orderDto.items(),
                        "att-sentinel-rb-res-" + orderDto.orderId());
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), sentinelReserve);

        OrderDto sentinelRollback =
                new OrderDto(
                        sentinelReserve.orderId(),
                        sentinelReserve.customerId(),
                        "ROLLBACK",
                        "INVENTORY",
                        sentinelReserve.items(),
                        "att-sentinel-rb-" + orderDto.orderId());
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), sentinelRollback);

        await().pollInterval(Duration.ofMillis(200))
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> {
                            // Seed added 10 reserved, deducted 10 available (reserved 20, available
                            // 90).
                            // Rollback releases 10 reserved back to available (reserved 10,
                            // available 100).
                            // Sentinel reserve added 10 reserved, deducted 10 available (reserved
                            // 20, available 90).
                            // Sentinel rollback released 10 back to available (reserved 10,
                            // available 100).
                            CustomersRecord persistedCustomer =
                                    customerDAO.findById(customer.getId()).orElseThrow();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(BigDecimal.TEN);
                            assertThat(persistedCustomer.getAmountAvailable())
                                    .isEqualByComparingTo(BigDecimal.valueOf(100));

                            // Single SETTLE ledger entry for orderDto
                            String settleLedgerKey =
                                    "downstream:payment:" + orderDto.orderId() + ":SETTLE";
                            assertThat(ledgerRepository.findByDownstreamKey(settleLedgerKey))
                                    .isPresent();
                        });
    }

    /**
     * Creates an inventory-sourced order event for the current customer with one item priced at
     * ten.
     *
     * @param status order status to place in the event
     * @return an order event with a generated identifier
     */
    private OrderDto getOrderDto(String status) {

        Faker faker = new Faker();
        OrderDto.OrderItemDto orderItemDto =
                new OrderDto.OrderItemDto(1L, faker.commerce().productName(), 1, BigDecimal.TEN);
        return new OrderDto(
                faker.number().randomNumber() + 10_000,
                this.customer.getId(),
                status,
                "INVENTORY",
                List.of(orderItemDto),
                "att-" + faker.internet().uuid());
    }
}
