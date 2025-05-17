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
        assertThat(result.source()).isNull();
        assertThat(result.orderId()).isEqualTo(testOrder.getId());
        assertThat(result.customerId()).isEqualTo(testOrder.getCustomerId());

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(updatedOrder.getSource()).isNull();

        // Verify the result matches what we would expect from the service logic
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(stockOrderDto.withStatusAndSource("CONFIRMED", null));
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
        assertThat(result.source()).isEqualTo("INVENTORY");
        assertThat(result.orderId()).isEqualTo(testOrder.getId());
        assertThat(result.customerId()).isEqualTo(testOrder.getCustomerId());

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(updatedOrder.getSource()).isEqualTo("INVENTORY");

        // Verify the result matches what we would expect based on the input
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(stockOrderDto.withStatusAndSource("REJECTED", "INVENTORY"));
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
        assertThat(result.orderId()).isEqualTo(testOrder.getId());
        assertThat(result.customerId()).isEqualTo(testOrder.getCustomerId());

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
        assertThat(updatedOrder.getSource()).isEqualTo("PAYMENT");

        // Verify the source is from payment service specifically
        assertThat(updatedOrder.getSource()).isNotEqualTo(stockOrderDto.source());
        assertThat(updatedOrder.getSource()).isEqualTo(paymentOrderDto.source());

        // Verify the result matches what we would expect based on the input
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(stockOrderDto.withStatusAndSource("ROLLBACK", "PAYMENT"));
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
        assertThat(result.orderId()).isEqualTo(testOrder.getId());
        assertThat(result.customerId()).isEqualTo(testOrder.getCustomerId());

        // Verify items array is preserved
        assertThat(result.items()).isEqualTo(stockOrderDto.items());

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
        assertThat(updatedOrder.getSource()).isEqualTo("INVENTORY");

        // Verify the source is from inventory service specifically
        assertThat(updatedOrder.getSource()).isEqualTo(stockOrderDto.source());
        assertThat(updatedOrder.getSource()).isNotEqualTo(paymentOrderDto.source());

        // Verify the result matches what we would expect based on the input
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(stockOrderDto.withStatusAndSource("ROLLBACK", "INVENTORY"));
    }

    @Test
    void verify_OrderDtoStructureIsPreservedAfterConfirmation() {
        // Arrange
        OrderDto paymentOrderDto = getPaymentOrderDto("ACCEPT", testOrder);
        OrderDto stockOrderDto = getStockOrderDto("ACCEPT", testOrder);

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, stockOrderDto);

        // Assert
        // Verify that the result has all original order properties except for the changed status
        // and source
        assertThat(result.orderId()).isEqualTo(stockOrderDto.orderId());
        assertThat(result.customerId()).isEqualTo(stockOrderDto.customerId());
        assertThat(result.items()).isEqualTo(stockOrderDto.items());

        // Verify that only status and source are changed
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.source()).isNull();

        // Verify database state after update
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(updatedOrder.getSource()).isNull();
        assertThat(updatedOrder.getCustomerId()).isEqualTo(testOrder.getCustomerId());
    }
}
