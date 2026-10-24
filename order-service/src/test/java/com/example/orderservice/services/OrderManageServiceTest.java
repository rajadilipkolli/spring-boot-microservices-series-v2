/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.common.dto.OrderDto;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.repositories.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderManageServiceTest {

    @Mock private OrderRepository orderRepository;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @InjectMocks private OrderManageService orderManageService;

    @BeforeEach
    void setUp() {
        when(orderRepository.updateOrderStatusAndSourceById(any(), any(), any())).thenReturn(1);
    }

    @Test
    void confirm_BothPaymentAndStockAreAccepted_ReturnsConfirmedOrder() {
        // Arrange
        OrderDto orderPayment =
                new OrderDto(
                        12345L, 67890L, "ACCEPT", "PAYMENT", Collections.emptyList(), "attempt-1");

        OrderDto orderStock =
                new OrderDto(
                        12345L,
                        67890L,
                        "ACCEPT",
                        "INVENTORY",
                        Collections.emptyList(),
                        "attempt-1");

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
        verify(meterRegistry.counter("orders_completed"), times(1)).increment();
    }

    @Test
    void confirm_BothPaymentAndStockAreRejected_ReturnsRejectedOrder() {
        // Arrange
        OrderDto orderPayment =
                new OrderDto(
                        12345L, 67890L, "REJECT", "PAYMENT", Collections.emptyList(), "attempt-1");

        OrderDto orderStock =
                new OrderDto(
                        12345L,
                        67890L,
                        "REJECT",
                        "INVENTORY",
                        Collections.emptyList(),
                        "attempt-1");

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
        verify(meterRegistry.counter("orders_failed"), times(1)).increment();
    }

    @Test
    void confirm_PaymentIsRejectedAndStockIsAccepted_ReturnsRollbackOrderWithPaymentAsSource() {
        // Arrange
        OrderDto orderPayment =
                new OrderDto(
                        12345L, 67890L, "REJECT", "PAYMENT", Collections.emptyList(), "attempt-1");

        OrderDto orderStock =
                new OrderDto(
                        12345L,
                        67890L,
                        "ACCEPT",
                        "INVENTORY",
                        Collections.emptyList(),
                        "attempt-1");

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
                new OrderDto(
                        12345L, 67890L, "ACCEPT", "PAYMENT", Collections.emptyList(), "attempt-1");

        OrderDto orderStock =
                new OrderDto(
                        12345L,
                        67890L,
                        "REJECT",
                        "INVENTORY",
                        Collections.emptyList(),
                        "attempt-1");

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
                new OrderDto(
                        12345L, 67890L, "ACCEPT", "PAYMENT", Collections.emptyList(), "attempt-1");

        OrderDto orderStock =
                new OrderDto(
                        12345L,
                        67890L,
                        "ACCEPT",
                        "INVENTORY",
                        Collections.emptyList(),
                        "attempt-1");

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
                new OrderDto(
                        12345L, 67890L, "ACCEPT", "PAYMENT", Collections.emptyList(), "attempt-1");

        OrderDto orderStock =
                new OrderDto(
                        12345L,
                        67890L,
                        "REJECT",
                        "INVENTORY",
                        Collections.emptyList(),
                        "attempt-1");

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

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {"CONFIRMED", "REJECTED", "ROLLBACK", "FAILED"})
    void lateResponsesDoNotRepublishPersistedTerminalOutcome(OrderStatus status) {
        var payment =
                new OrderDto(1L, 2L, "ACCEPT", "PAYMENT", Collections.emptyList(), "attempt-2");
        var stock = payment.withStatusAndSource("REJECT", "INVENTORY");
        var persisted = new Order();
        persisted.setStatus(status);
        persisted.setSource("original-source");
        when(orderRepository.updateOrderStatusAndSourceById(any(), any(), any())).thenReturn(0);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(persisted));

        assertThat(orderManageService.confirm(payment, stock)).isNull();
        verify(meterRegistry.counter("orders_completed"), never()).increment();
        verify(meterRegistry.counter("orders_failed"), never()).increment();
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {"NEW", "ACCEPT"})
    void zeroRowUpdatePreservesPersistedNonterminalResponse(OrderStatus status) {
        var payment =
                new OrderDto(1L, 2L, "ACCEPT", "PAYMENT", Collections.emptyList(), "attempt-2");
        var stock = payment.withStatusAndSource("ACCEPT", "INVENTORY");
        var persisted = new Order();
        persisted.setStatus(status);
        persisted.setSource("original-source");
        when(orderRepository.updateOrderStatusAndSourceById(any(), any(), any())).thenReturn(0);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(persisted));

        assertThat(orderManageService.confirm(payment, stock))
                .isEqualTo(stock.withStatusAndSource(status.name(), "original-source"));
        verify(meterRegistry.counter("orders_completed"), never()).increment();
        verify(meterRegistry.counter("orders_failed"), never()).increment();
    }

    @Test
    void rejectsResponsesFromDifferentAttempts() {
        var payment =
                new OrderDto(1L, 2L, "ACCEPT", "PAYMENT", Collections.emptyList(), "attempt-1");
        reset(orderRepository);
        assertThatThrownBy(
                        () ->
                                orderManageService.confirm(
                                        payment, payment.withAttemptId("attempt-2")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(orderRepository);
    }
}
