/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.entities.OrderStatus;
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
        assertThat(actual).isNotNull();
        assertThat(actual.status()).isEqualTo("CONFIRMED");
        assertThat(actual.source()).isNull();
        assertThat(actual.orderId()).isEqualTo(12345L);
        assertThat(actual.customerId()).isEqualTo(67890L);
        assertThat(actual.items()).isEmpty();

        // Additional assertions
        assertThat(actual)
                .usingRecursiveComparison()
                .isEqualTo(orderStock.withStatusAndSource("CONFIRMED", null));
        assertThat(actual).isExactlyInstanceOf(OrderDto.class);
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
        assertThat(actual).isNotNull();
        assertThat(actual.status()).isEqualTo("REJECTED");
        assertThat(actual.source()).isEqualTo("INVENTORY");
        assertThat(actual.orderId()).isEqualTo(12345L);
        assertThat(actual.customerId()).isEqualTo(67890L);
        assertThat(actual.items()).isEmpty();
        // Additional assertions
        assertThat(actual)
                .usingRecursiveComparison()
                .isEqualTo(orderStock.withStatusAndSource("REJECTED", "INVENTORY"));
        assertThat(actual.source()).isEqualTo(orderStock.source());
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
        assertThat(actual).isNotNull();
        assertThat(actual.status()).isEqualTo("ROLLBACK");
        assertThat(actual.source()).isEqualTo("PAYMENT");
        assertThat(actual.orderId()).isEqualTo(12345L);
        assertThat(actual.customerId()).isEqualTo(67890L);
        assertThat(actual.items()).isEmpty();

        // Additional assertions
        assertThat(actual)
                .usingRecursiveComparison()
                .isEqualTo(orderStock.withStatusAndSource("ROLLBACK", "PAYMENT"));
        assertThat(actual.status()).isEqualTo("ROLLBACK");
        assertThat(actual.source()).isNotEqualTo(orderStock.source());
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
        assertThat(actual).isNotNull();
        assertThat(actual.status()).isEqualTo("ROLLBACK");
        assertThat(actual.source()).isEqualTo("INVENTORY");
        assertThat(actual.orderId()).isEqualTo(12345L);
        assertThat(actual.customerId()).isEqualTo(67890L);
        assertThat(actual.items()).isEmpty();

        // Additional assertions
        assertThat(actual)
                .usingRecursiveComparison()
                .isEqualTo(orderStock.withStatusAndSource("ROLLBACK", "INVENTORY"));
        assertThat(actual.status()).isEqualTo("ROLLBACK");
        assertThat(actual.source()).isEqualTo(orderStock.source());
    }

    @Test
    void confirm_VerifiesRepositoryIsCalledWithCorrectParameters() {
        // Arrange
        OrderDto orderPayment =
                new OrderDto(12345L, 67890L, "ACCEPT", "PAYMENT", Collections.emptyList());

        OrderDto orderStock =
                new OrderDto(12345L, 67890L, "ACCEPT", "INVENTORY", Collections.emptyList());

        when(orderRepository.updateOrderStatusAndSourceById(any(), any(), any())).thenReturn(1);

        // Act
        OrderDto result = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        verify(orderRepository, times(1))
                .updateOrderStatusAndSourceById(eq(12345L), eq(OrderStatus.CONFIRMED), eq(null));

        // Additional assertions for the returned OrderDto
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.source()).isNull();
        assertThat(result.orderId()).isEqualTo(12345L);
        assertThat(result.customerId()).isEqualTo(67890L);

        // Verify no other interactions with the repository
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void confirm_RollbackCase_VerifiesRepositoryIsCalledWithCorrectParameters() {
        // Arrange
        OrderDto orderPayment =
                new OrderDto(12345L, 67890L, "ACCEPT", "PAYMENT", Collections.emptyList());

        OrderDto orderStock =
                new OrderDto(12345L, 67890L, "REJECT", "INVENTORY", Collections.emptyList());

        when(orderRepository.updateOrderStatusAndSourceById(any(), any(), any())).thenReturn(1);

        // Act
        OrderDto result = orderManageService.confirm(orderPayment, orderStock);

        // Assert
        verify(orderRepository, times(1))
                .updateOrderStatusAndSourceById(
                        eq(12345L), eq(OrderStatus.ROLLBACK), eq("INVENTORY"));

        // Additional assertions
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("ROLLBACK");
        assertThat(result.source()).isEqualTo("INVENTORY");

        // Verify the return value matches what would be expected from the service's logic
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(orderStock.withStatusAndSource("ROLLBACK", "INVENTORY"));
    }
}
