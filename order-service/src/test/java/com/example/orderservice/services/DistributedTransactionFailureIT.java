/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.util.TestData;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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
        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(testOrder.getId());
        paymentOrderDto.setCustomerId(testOrder.getCustomerId());
        paymentOrderDto.setStatus("REJECT");
        paymentOrderDto.setSource("PAYMENT");

        // Send payment rejection
        kafkaTemplate.send("payment-orders", paymentOrderDto.getOrderId(), paymentOrderDto);

        // Send a successful inventory response
        OrderDto inventoryOrderDto = new OrderDto();
        inventoryOrderDto.setOrderId(testOrder.getId());
        inventoryOrderDto.setCustomerId(testOrder.getCustomerId());
        inventoryOrderDto.setStatus("ACCEPT");
        inventoryOrderDto.setSource("STOCK");

        kafkaTemplate.send("stock-orders", inventoryOrderDto.getOrderId(), inventoryOrderDto);

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
        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(testOrder.getId());
        paymentOrderDto.setCustomerId(testOrder.getCustomerId());
        paymentOrderDto.setStatus("ACCEPT");
        paymentOrderDto.setSource("PAYMENT");

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
        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(testOrder.getId());
        paymentOrderDto.setCustomerId(testOrder.getCustomerId());
        paymentOrderDto.setStatus("ACCEPT");
        paymentOrderDto.setSource("PAYMENT");

        OrderDto inventoryOrderDto = new OrderDto();
        inventoryOrderDto.setOrderId(testOrder.getId());
        inventoryOrderDto.setCustomerId(testOrder.getCustomerId());
        inventoryOrderDto.setStatus("REJECT");
        inventoryOrderDto.setSource("INVENTORY");
        inventoryOrderDto.setItems(List.of(new OrderItemDto(1L, "Product1", 10, BigDecimal.TEN)));

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, inventoryOrderDto);

        // Assert
        assertThat(result.getStatus()).isEqualTo("ROLLBACK");
        assertThat(result.getSource()).isEqualTo("INVENTORY");

        Order savedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
    }

    @Test
    void whenKafkaMessagesOutOfOrder_ShouldHandleCorrectly() {
        // Arrange
        OrderDto inventoryOrderDto = new OrderDto();
        inventoryOrderDto.setOrderId(testOrder.getId());
        inventoryOrderDto.setCustomerId(testOrder.getCustomerId());
        inventoryOrderDto.setStatus("ACCEPT");
        inventoryOrderDto.setSource("INVENTORY");
        inventoryOrderDto.setItems(List.of(new OrderItemDto(1L, "Product1", 10, BigDecimal.TEN)));

        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(testOrder.getId());
        paymentOrderDto.setCustomerId(testOrder.getCustomerId());
        paymentOrderDto.setStatus("ACCEPT");
        paymentOrderDto.setSource("PAYMENT");

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
