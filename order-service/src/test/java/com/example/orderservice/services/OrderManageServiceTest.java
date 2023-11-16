/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.repositories.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderManageServiceTest {

    @Mock private OrderRepository orderRepository;

    @InjectMocks private OrderManageService orderManageService;

    @Test
    void testConfirm_BothPaymentAndStockAreAccepted_ReturnsConfirmedOrder() {
        // Arrange
        OrderDto orderPayment = new OrderDto();
        orderPayment.setOrderId(12345L);
        orderPayment.setCustomerId(67890L);
        orderPayment.setStatus("ACCEPT");

        OrderDto orderStock = new OrderDto();
        orderStock.setOrderId(12345L);
        orderStock.setCustomerId(67890L);
        orderStock.setStatus("ACCEPT");

        // Act
        OrderDto actual = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        assertThat(actual.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void testConfirm_BothPaymentAndStockAreRejected_ReturnsRejectedOrder() {
        // Arrange
        OrderDto orderPayment = new OrderDto();
        orderPayment.setOrderId(12345L);
        orderPayment.setCustomerId(67890L);
        orderPayment.setStatus("REJECT");

        OrderDto orderStock = new OrderDto();
        orderStock.setOrderId(12345L);
        orderStock.setCustomerId(67890L);
        orderStock.setStatus("REJECT");

        // Act
        OrderDto actual = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        assertThat(actual.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void testConfirm_PaymentIsRejectedAndStockIsAccepted_ReturnsRollbackOrderWithPaymentAsSource() {
        // Arrange
        OrderDto orderPayment = new OrderDto();
        orderPayment.setOrderId(12345L);
        orderPayment.setCustomerId(67890L);
        orderPayment.setStatus("REJECT");

        OrderDto orderStock = new OrderDto();
        orderStock.setOrderId(12345L);
        orderStock.setCustomerId(67890L);
        orderStock.setStatus("ACCEPT");

        // Act
        OrderDto actual = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        assertThat(actual.getStatus()).isEqualTo("ROLLBACK");
        assertThat(actual.getSource()).isEqualTo("PAYMENT");
    }

    @Test
    void testConfirm_PaymentIsAcceptedAndStockIsRejected_ReturnsRollbackOrderWithStockAsSource() {
        // Arrange
        OrderDto orderPayment = new OrderDto();
        orderPayment.setOrderId(12345L);
        orderPayment.setCustomerId(67890L);
        orderPayment.setStatus("ACCEPT");

        OrderDto orderStock = new OrderDto();
        orderStock.setOrderId(12345L);
        orderStock.setCustomerId(67890L);
        orderStock.setStatus("REJECT");

        // Act
        OrderDto actual = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        assertThat(actual.getStatus()).isEqualTo("ROLLBACK");
        assertThat(actual.getSource()).isEqualTo("INVENTORY");
    }
}
