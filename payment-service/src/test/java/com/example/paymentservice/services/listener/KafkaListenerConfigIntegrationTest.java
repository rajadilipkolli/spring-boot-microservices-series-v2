/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class KafkaListenerConfigIntegrationTest extends AbstractIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(KafkaListenerConfigIntegrationTest.class);

    @MockitoSpyBean private KafkaListenerConfig listenerSpy;

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
                                verify(listenerSpy)
                                        .dlt(
                                                contains(orderDto.orderId().toString()),
                                                eq("orders-dlt-payment")));
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
