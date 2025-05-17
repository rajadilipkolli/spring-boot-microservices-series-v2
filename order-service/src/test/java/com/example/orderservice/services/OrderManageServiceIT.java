/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static com.example.orderservice.util.TestData.getPaymentOrderDto;
import static com.example.orderservice.util.TestData.getStockOrderDto;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.util.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderManageServiceIT extends AbstractIntegrationTest {

    @Autowired private OrderManageService orderManageService;

    @Autowired private OrderRepository orderRepository;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        testOrder = TestData.getOrder();
        testOrder = orderRepository.save(testOrder);
    }

    @Test
    void confirm_BothPaymentAndStockAreAccepted_ShouldUpdateOrderStatusToConfirmed() {
        // Arrange
        OrderDto paymentOrderDto = getPaymentOrderDto("ACCEPT", testOrder);

        OrderDto stockOrderDto = getStockOrderDto("ACCEPT", testOrder);

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, stockOrderDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("CONFIRMED");

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_BothPaymentAndStockAreRejected_ShouldUpdateOrderStatusToRejected() {
        // Arrange
        OrderDto paymentOrderDto = getPaymentOrderDto("REJECT", testOrder);

        OrderDto stockOrderDto = getStockOrderDto("REJECT", testOrder);

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, stockOrderDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("REJECTED");

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void
            confirm_PaymentIsRejectedAndStockIsAccepted_ShouldUpdateOrderStatusToRollbackWithPaymentSource() {
        // Arrange
        OrderDto paymentOrderDto = getPaymentOrderDto("REJECT", testOrder);

        OrderDto stockOrderDto = getStockOrderDto("ACCEPT", testOrder);

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, stockOrderDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("ROLLBACK");
        assertThat(result.source()).isEqualTo("PAYMENT");

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
        assertThat(updatedOrder.getSource()).isEqualTo("PAYMENT");
    }

    @Test
    void
            confirm_PaymentIsAcceptedAndStockIsRejected_ShouldUpdateOrderStatusToRollbackWithInventorySource() {
        // Arrange
        OrderDto paymentOrderDto = getPaymentOrderDto("ACCEPT", testOrder);

        OrderDto stockOrderDto = getStockOrderDto("REJECT", testOrder);

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, stockOrderDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("ROLLBACK");
        assertThat(result.source()).isEqualTo("INVENTORY");

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
        assertThat(updatedOrder.getSource()).isEqualTo("INVENTORY");
    }
}
