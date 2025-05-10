/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.util.TestData;
import java.math.BigDecimal;
import java.util.List;
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
        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(testOrder.getId());
        paymentOrderDto.setCustomerId(testOrder.getCustomerId());
        paymentOrderDto.setStatus("ACCEPT");
        paymentOrderDto.setSource("PAYMENT");

        OrderDto stockOrderDto = getStockOrderDto("ACCEPT");

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, stockOrderDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("CONFIRMED");

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    private OrderDto getStockOrderDto(String status) {
        OrderDto stockOrderDto = new OrderDto();
        stockOrderDto.setOrderId(testOrder.getId());
        stockOrderDto.setCustomerId(testOrder.getCustomerId());
        stockOrderDto.setStatus(status);
        stockOrderDto.setSource("INVENTORY");

        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setItemId(1L);
        orderItemDto.setProductId("Product1");
        orderItemDto.setProductPrice(BigDecimal.TEN);
        orderItemDto.setQuantity(10);
        stockOrderDto.setItems(List.of(orderItemDto));
        return stockOrderDto;
    }

    @Test
    void confirm_BothPaymentAndStockAreRejected_ShouldUpdateOrderStatusToRejected() {
        // Arrange
        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(testOrder.getId());
        paymentOrderDto.setCustomerId(testOrder.getCustomerId());
        paymentOrderDto.setStatus("REJECT");
        paymentOrderDto.setSource("PAYMENT");

        OrderDto stockOrderDto = getStockOrderDto("REJECT");

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, stockOrderDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void
            confirm_PaymentIsRejectedAndStockIsAccepted_ShouldUpdateOrderStatusToRollbackWithPaymentSource() {
        // Arrange
        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(testOrder.getId());
        paymentOrderDto.setCustomerId(testOrder.getCustomerId());
        paymentOrderDto.setStatus("REJECT");
        paymentOrderDto.setSource("PAYMENT");

        OrderDto stockOrderDto = getStockOrderDto("ACCEPT");

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, stockOrderDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ROLLBACK");
        assertThat(result.getSource()).isEqualTo("PAYMENT");

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
        assertThat(updatedOrder.getSource()).isEqualTo("PAYMENT");
    }

    @Test
    void
            confirm_PaymentIsAcceptedAndStockIsRejected_ShouldUpdateOrderStatusToRollbackWithInventorySource() {
        // Arrange
        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(testOrder.getId());
        paymentOrderDto.setCustomerId(testOrder.getCustomerId());
        paymentOrderDto.setStatus("ACCEPT");
        paymentOrderDto.setSource("PAYMENT");

        OrderDto stockOrderDto = getStockOrderDto("REJECT");

        // Act
        OrderDto result = orderManageService.confirm(paymentOrderDto, stockOrderDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ROLLBACK");
        assertThat(result.getSource()).isEqualTo("INVENTORY");

        // Verify database was updated
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.ROLLBACK);
        assertThat(updatedOrder.getSource()).isEqualTo("INVENTORY");
    }
}
