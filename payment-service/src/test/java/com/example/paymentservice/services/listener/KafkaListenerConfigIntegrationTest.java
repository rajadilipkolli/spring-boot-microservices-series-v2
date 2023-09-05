/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.repositories.CustomerRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaListenerConfigIntegrationTest extends AbstractIntegrationTest {

    @Autowired private KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @Autowired private CustomerRepository customerRepository;

    @Autowired private KafkaListenerConfig kafkaListenerConfig;

    private Customer customer;

    @BeforeEach
    void setUp() {
        this.customerRepository.deleteAll();
        customer =
                this.customerRepository.save(
                        new Customer(
                                null,
                                "First Customer",
                                "first@customer.email",
                                "First Address",
                                100,
                                10));
    }

    @Test
    void onEventReserveOrder() {
        OrderDto orderDto = getOrderDto("NEW");

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
                            assertThat(persistedCustomer.getAmountReserved()).isEqualTo(20);
                            assertThat(persistedCustomer.getAmountAvailable()).isEqualTo(90);
                        });
    }

    @Test
    void onEventReserveOrderDlt() {
        OrderDto orderDto = getOrderDto("NEW");
        long customerId = orderDto.getCustomerId() + 10_000;
        orderDto.setCustomerId(customerId);

        // When
        kafkaTemplate.send("orders", orderDto.getOrderId(), orderDto);

        assertThat(kafkaListenerConfig.getDeadLetterLatch().getCount()).isEqualTo(1);
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
                            assertThat(persistedCustomer.getAmountReserved()).isZero();
                            assertThat(persistedCustomer.getAmountAvailable()).isEqualTo(110);
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
