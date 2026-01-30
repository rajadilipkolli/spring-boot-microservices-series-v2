/*** Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli. ***/
package com.example.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.util.TestData;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentOrderManageServiceTest {

    @Mock private CustomerRepository customerRepository;

    @Mock private KafkaTemplate<String, OrderDto> kafkaTemplate;

    @InjectMocks private PaymentOrderManageService orderManageService;

    @Test
    void confirmWithValidOrder() {
        // Arrange
        OrderItemDto orderItemDto = new OrderItemDto(1L, "productId", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "CONFIRMED", null, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        // Act
        orderManageService.confirm(orderDto);

        // Assert
        assertThat(customer.getAmountReserved()).isZero();
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @ParameterizedTest
    @CsvSource({"INVENTORY,1100, 0", "PAYMENT,1000, 100"})
    void confirmWithRejectedOrder(String source, int amountAvailable, int amountReserved) {
        // Arrange
        OrderItemDto orderItemDto = new OrderItemDto(1L, "productId", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "ROLLBACK", source, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // Act
        orderManageService.confirm(orderDto);

        // Assert
        assertThat(customer.getAmountReserved()).isEqualTo(amountReserved);
        assertThat(customer.getAmountAvailable()).isEqualTo(amountAvailable);
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    void confirmWithInvalidCustomer() {
        // Arrange
        OrderDto orderDto = new OrderDto(1L, 1L, "CONFIRMED", null, null);
        given(customerRepository.findById(1L)).willReturn(Optional.empty());

        // Assert
        assertThatExceptionOfType(CustomerNotFoundException.class)
                .isThrownBy(() -> orderManageService.confirm(orderDto));
    }

    @Test
    void reserveWithValidOrderAccepted() {
        // Arrange
        OrderItemDto orderItemDto = new OrderItemDto(1L, "productId", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "CONFIRMED", null, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        // Act
        OrderDto reservedOrder = orderManageService.reserve(orderDto);

        // Assert
        assertThat(customer.getAmountReserved()).isEqualTo(200);
        assertThat(customer.getAmountAvailable()).isEqualTo(900);
        assertThat(reservedOrder.source()).isEqualTo("PAYMENT");
        assertThat(reservedOrder.status()).isEqualTo("ACCEPT");
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    void reserveWithValidOrderRejected() {
        // Arrange
        OrderItemDto orderItemDto = new OrderItemDto(1L, "productId", 1000, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "CONFIRMED", null, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        // Act
        OrderDto reservedOrder = orderManageService.reserve(orderDto);

        // Assert
        assertThat(customer.getAmountReserved()).isEqualTo(100);
        assertThat(customer.getAmountAvailable()).isEqualTo(1000);
        assertThat(reservedOrder.status()).isEqualTo("REJECT");
        assertThat(reservedOrder.source()).isEqualTo("PAYMENT");
        verify(customerRepository, times(1)).save(any(Customer.class));
    }
}
