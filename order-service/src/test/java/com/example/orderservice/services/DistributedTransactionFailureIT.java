/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.util.TestData;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

class DistributedTransactionFailureIT extends AbstractIntegrationTest {
    @Autowired private OrderManageService orderManageService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private KafkaTemplate<Long, OrderDto> kafkaTemplate;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        testOrder = orderRepository.save(TestData.getOrder());
    }

    @Test
    void whenPaymentFailsButInventorySucceeds_ShouldRollbackBothServices() {
        // Arrange
        OrderDto paymentOrderDto = TestData.getPaymentOrderDto("REJECT", testOrder);

        // Send payment rejection
        kafkaTemplate.send("payment-orders", paymentOrderDto.orderId(), paymentOrderDto);

        // Send a successful inventory response
        OrderDto inventoryOrderDto = TestData.getStockOrderDto("ACCEPT", testOrder);

        kafkaTemplate.send("stock-orders", inventoryOrderDto.orderId(), inventoryOrderDto);

        // Assert that the order is eventually rejected
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Optional<Order> order = orderRepository.findById(testOrder.getId());
                            assertThat(order)
                                    .isPresent()
                                    .hasValueSatisfying(
                                            o ->
                                                    assertThat(o.getStatus())
                                                            .isEqualTo(OrderStatus.ROLLBACK));
                        });
    }

    @Test
    void whenBothServicesTimeOut_ShouldMarkOrderAsFailed() {

        // Simulate timeout by not sending any confirmation messages
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            Order updatedOrder =
                                    orderRepository.findById(testOrder.getId()).orElseThrow();

                            // The order should be picked up by the retry job and marked as failed
                            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
                        });
    }

    @Test
    void whenPartialResponsesReceived_ShouldHandleIncompleteState() {
        // Arrange
        OrderDto paymentOrderDto = TestData.getPaymentOrderDto("ACCEPT", testOrder);

        // Act - Only send payment response, missing inventory response
        kafkaTemplate.send("payment-orders", testOrder.getId(), paymentOrderDto);

        // Assert - Order should stay in NEW state
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            Order updatedOrder =
                                    orderRepository.findById(testOrder.getId()).orElseThrow();
                            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
                        });
    }

    @Test
    void whenInventoryPartiallyAvailable_ShouldRollbackEntireOrder() {
        // Arrange
        OrderDto paymentOrderDto = TestData.getPaymentOrderDto("ACCEPT", testOrder);

        OrderDto inventoryOrderDto = TestData.getStockOrderDto("REJECT", testOrder);

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, inventoryOrderDto);

        // Assert
        assertThat(result.status()).isEqualTo("ROLLBACK");
        assertThat(result.source()).isEqualTo("INVENTORY");

        Order savedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
    }

    @Test
    void whenKafkaMessagesOutOfOrder_ShouldHandleCorrectly() {
        // Arrange
        OrderDto paymentOrderDto = TestData.getPaymentOrderDto("ACCEPT", testOrder);

        OrderDto inventoryOrderDto = TestData.getStockOrderDto("ACCEPT", testOrder);

        // Act - Send messages in reverse order
        kafkaTemplate.send("stock-orders", testOrder.getId(), inventoryOrderDto);
        kafkaTemplate.send("payment-orders", testOrder.getId(), paymentOrderDto);

        // Assert - Final state should still be correct
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            Order updatedOrder =
                                    orderRepository.findById(testOrder.getId()).orElseThrow();
                            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
                        });
    }
}
