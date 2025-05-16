/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.repositories.OrderRepository;
import java.util.Collections;
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
    void confirm_BothPaymentAndStockAreAccepted_ReturnsConfirmedOrder() {
        // Arrange
        OrderDto orderPayment =
                new OrderDto(12345L, 67890L, "ACCEPT", "PAYMENT", Collections.emptyList());

        OrderDto orderStock =
                new OrderDto(12345L, 67890L, "ACCEPT", "INVENTORY", Collections.emptyList());

        // Act
        OrderDto actual = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        assertThat(actual.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirm_BothPaymentAndStockAreRejected_ReturnsRejectedOrder() {
        // Arrange
        OrderDto orderPayment =
                new OrderDto(12345L, 67890L, "REJECT", "PAYMENT", Collections.emptyList());

        OrderDto orderStock =
                new OrderDto(12345L, 67890L, "REJECT", "INVENTORY", Collections.emptyList());

        // Act
        OrderDto actual = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        assertThat(actual.status()).isEqualTo("REJECTED");
    }

    @Test
    void confirm_PaymentIsRejectedAndStockIsAccepted_ReturnsRollbackOrderWithPaymentAsSource() {
        // Arrange
        OrderDto orderPayment =
                new OrderDto(12345L, 67890L, "REJECT", "PAYMENT", Collections.emptyList());

        OrderDto orderStock =
                new OrderDto(12345L, 67890L, "ACCEPT", "INVENTORY", Collections.emptyList());

        // Act
        OrderDto actual = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        assertThat(actual.status()).isEqualTo("ROLLBACK");
        assertThat(actual.source()).isEqualTo("PAYMENT");
    }

    @Test
    void confirm_PaymentIsAcceptedAndStockIsRejected_ReturnsRollbackOrderWithStockAsSource() {
        // Arrange
        OrderDto orderPayment =
                new OrderDto(12345L, 67890L, "ACCEPT", "PAYMENT", Collections.emptyList());

        OrderDto orderStock =
                new OrderDto(12345L, 67890L, "REJECT", "INVENTORY", Collections.emptyList());

        // Act
        OrderDto actual = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        assertThat(actual.status()).isEqualTo("ROLLBACK");
        assertThat(actual.source()).isEqualTo("INVENTORY");
    }
}
