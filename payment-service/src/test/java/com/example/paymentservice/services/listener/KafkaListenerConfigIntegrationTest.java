/*** Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.entities.Customer;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KafkaListenerConfigIntegrationTest extends AbstractIntegrationTest {

    private Customer customer;

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
                                .setAddress("First Address")
                                .setAmountAvailable(100)
                                .setAmountReserved(10));
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

    @Test
    void onEventReserveOrder() {
        OrderDto orderDto = getOrderDto("NEW");

        double amountReserved = customer.getAmountReserved();
        double amountAvailable = customer.getAmountAvailable();

        // When
        kafkaTemplate.send("orders", orderDto.getOrderId(), orderDto);

        // Then
        await().pollDelay(3, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            Customer persistedCustomer =
                                    customerRepository.findById(customer.getId()).get();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualTo(amountReserved + 10);
                            assertThat(persistedCustomer.getAmountAvailable())
                                    .isEqualTo(amountAvailable - 10);
                        });
    }

    @Test
    void onEventReserveOrderDlt() {
        OrderDto orderDto = getOrderDto("NEW");
        // Use a non-existent customerId by adding a large offset
        long nonExistentCustomerId = orderDto.getCustomerId() + 10_000;
        orderDto.setCustomerId(nonExistentCustomerId);

        // When
        kafkaTemplate.send("orders", orderDto.getOrderId(), orderDto);

        // Then
        await().pollDelay(3, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(kafkaListenerConfig.getDeadLetterLatch().getCount())
                                        .isZero());
    }

    @Test
    void onEventConfirmOrder() {

        OrderDto orderDto = getOrderDto("ROLLBACK");

        double amountReserved = customer.getAmountReserved();
        double amountAvailable = customer.getAmountAvailable();

        // When
        kafkaTemplate.send("orders", orderDto.getOrderId(), orderDto);

        // Then
        await().pollDelay(3, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            Customer persistedCustomer =
                                    customerRepository.findById(customer.getId()).get();
                            assertThat(persistedCustomer.getAmountReserved())
                                    .isEqualTo(amountReserved - 10);
                            assertThat(persistedCustomer.getAmountAvailable())
                                    .isEqualTo(amountAvailable + 10);
                        });
    }

    @Test
    void onEventConfirmOrderNoRollBack() {

        OrderDto orderDto = getOrderDto("ROLLBACK");
        orderDto.setSource("PAYMENT");

        // When
        kafkaTemplate.send("orders", orderDto.getOrderId(), orderDto);

        // Then
        await().pollDelay(3, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            Customer persistedCustomer =
                                    customerRepository.findById(customer.getId()).get();
                            assertThat(persistedCustomer.getAmountReserved()).isEqualTo(10);
                            assertThat(persistedCustomer.getAmountAvailable()).isEqualTo(100);
                        });
    }

    private OrderDto getOrderDto(String status) {
        Faker faker = new Faker();
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(faker.number().randomNumber());
        orderDto.setStatus(status);
        orderDto.setSource("INVENTORY");
        // Use the exact ID from the customer that was just saved in setUp()
        orderDto.setCustomerId(customer.getId());

        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setProductPrice(BigDecimal.TEN);
        orderItemDto.setQuantity(1);
        orderItemDto.setProductId("P0001");
        orderItemDto.setItemId(1L);
        orderDto.setItems(List.of(orderItemDto));
        return orderDto;
    }
}
