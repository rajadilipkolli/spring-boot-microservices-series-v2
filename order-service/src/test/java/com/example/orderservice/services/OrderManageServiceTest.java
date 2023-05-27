/* Licensed under Apache-2.0 2023 */
package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.repositories.OrderRepository;
import org.junit.jupiter.api.Assertions;
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
        Assertions.assertEquals("CONFIRMED", actual.getStatus());
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
        Assertions.assertEquals("REJECTED", actual.getStatus());
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
        Assertions.assertEquals("ROLLBACK", actual.getStatus());
        Assertions.assertEquals("PAYMENT", actual.getSource());
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
        Assertions.assertEquals("ROLLBACK", actual.getStatus());
        Assertions.assertEquals("STOCK", actual.getSource());
    }
}
