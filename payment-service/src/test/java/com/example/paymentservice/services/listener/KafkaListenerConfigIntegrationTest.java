/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.util.TestData;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KafkaListenerConfigIntegrationTest extends AbstractIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(KafkaListenerConfigIntegrationTest.class);
    private Customer customer;

    /** Replaces existing customers with a uniquely named customer and verifies it was persisted. */
    @BeforeEach
    void setUp() {
        this.customerRepository.deleteAll();
        // Generate unique data for each test to avoid sequence conflicts
        Faker faker = new Faker();
        String uniqueName = "Customer_" + faker.number().randomNumber();
        String uniqueEmail = "email_" + faker.number().randomNumber() + "@example.com";

        customer =
                this.customerRepository.save(
                        new Customer()
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
                                .setAmountReserved(BigDecimal.TEN));
        // Ensure the customer is saved before running tests
        assertThat(customer).isNotNull();
        assertThat(this.customerRepository.findById(customer.getId()))
                .isPresent()
                .get()
                .satisfies(
                        customer -> {
                            assertThat(customer.getName()).isEqualTo(uniqueName);
                            assertThat(customer.getEmail()).isEqualTo(uniqueEmail);
                            assertThat(customer.getId()).isNotEqualTo(1);
                        });
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
        await().pollDelay(3, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            Customer persistedCustomer =
                                    customerRepository.findById(customer.getId()).get();
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

        // Then
        await().pollDelay(3, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
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

        BigDecimal amountReserved = customer.getAmountReserved();
        BigDecimal amountAvailable = customer.getAmountAvailable();

        // When
        log.debug("Sending order DTO: {}", orderDto);
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);

        // Then
        await().pollDelay(3, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            Customer persistedCustomer =
                                    customerRepository.findById(customer.getId()).get();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualByComparingTo(amountReserved.subtract(BigDecimal.TEN));
                            assertThat(persistedCustomer.getAmountAvailable())
                                    .isEqualByComparingTo(amountAvailable.add(BigDecimal.TEN));
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
        await().pollDelay(3, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            Customer persistedCustomer =
                                    customerRepository.findById(customer.getId()).get();
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
                List.of(orderItemDto));
    }
}
