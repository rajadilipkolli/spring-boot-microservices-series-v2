/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.exception.CustomerNotFoundException;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.util.TestData;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentOrderManageServiceTest {

    @Mock private CustomerRepository customerRepository;

    @Mock private KafkaTemplate<String, OrderDto> kafkaTemplate;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @InjectMocks private PaymentOrderManageService orderManageService;

    @Test
    void confirmWithValidOrder() {
        // Arrange
        OrderDto.OrderItemDto orderItemDto =
                new OrderDto.OrderItemDto(1L, "productId", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "CONFIRMED", null, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        // Act
        orderManageService.confirm(orderDto);

        // Assert
        assertThat(customer.getAmountReserved()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @ParameterizedTest
    @CsvSource({"INVENTORY,1100, 0", "PAYMENT,1000, 100"})
    void confirmWithRejectedOrder(
            String source, BigDecimal amountAvailable, BigDecimal amountReserved) {
        // Arrange
        OrderDto.OrderItemDto orderItemDto =
                new OrderDto.OrderItemDto(1L, "productId", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "ROLLBACK", source, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // Act
        orderManageService.confirm(orderDto);

        // Assert
        assertThat(customer.getAmountReserved()).isEqualByComparingTo(amountReserved);
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo(amountAvailable);
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
        OrderDto.OrderItemDto orderItemDto =
                new OrderDto.OrderItemDto(1L, "productId", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "CONFIRMED", null, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        // Act
        OrderDto reservedOrder = orderManageService.reserve(orderDto);

        // Assert
        assertThat(customer.getAmountReserved()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo(new BigDecimal("900"));
        assertThat(reservedOrder.source()).isEqualTo("PAYMENT");
        assertThat(reservedOrder.status()).isEqualTo("ACCEPT");
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    void reserveWithValidOrderRejected() {
        // Arrange
        OrderDto.OrderItemDto orderItemDto =
                new OrderDto.OrderItemDto(1L, "productId", 1000, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "CONFIRMED", null, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        // Act
        OrderDto reservedOrder = orderManageService.reserve(orderDto);

        // Assert
        assertThat(customer.getAmountReserved()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(reservedOrder.status()).isEqualTo("REJECT");
        assertThat(reservedOrder.source()).isEqualTo("PAYMENT");
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    void fractionalOrderTest() {
        // Arrange
        OrderDto.OrderItemDto orderItemDto =
                new OrderDto.OrderItemDto(1L, "productId", 1, new BigDecimal("19.99"));
        OrderDto orderDto = new OrderDto(1L, 1L, "NEW", null, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        BigDecimal initialAvailable = customer.getAmountAvailable();
        BigDecimal initialReserved = customer.getAmountReserved();

        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // Reserve
        OrderDto reservedOrder = orderManageService.reserve(orderDto);
        assertThat(reservedOrder.status()).isEqualTo("ACCEPT");
        assertThat(customer.getAmountReserved())
                .isEqualByComparingTo(initialReserved.add(new BigDecimal("19.99")));
        assertThat(customer.getAmountAvailable())
                .isEqualByComparingTo(initialAvailable.subtract(new BigDecimal("19.99")));

        // Confirm
        OrderDto confirmDto = new OrderDto(1L, 1L, "CONFIRMED", null, List.of(orderItemDto));
        orderManageService.confirm(confirmDto);
        assertThat(customer.getAmountReserved()).isEqualByComparingTo(initialReserved);
        assertThat(customer.getAmountAvailable())
                .isEqualByComparingTo(initialAvailable.subtract(new BigDecimal("19.99")));
    }

    @Test
    void rollbackFractionalOrderTest() {
        // Arrange
        OrderDto.OrderItemDto orderItemDto =
                new OrderDto.OrderItemDto(1L, "productId", 1, new BigDecimal("19.99"));
        OrderDto orderDto = new OrderDto(1L, 1L, "NEW", null, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        BigDecimal initialAvailable = customer.getAmountAvailable();
        BigDecimal initialReserved = customer.getAmountReserved();

        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // Reserve
        OrderDto reservedOrder = orderManageService.reserve(orderDto);
        assertThat(reservedOrder.status()).isEqualTo("ACCEPT");

        // Rollback
        OrderDto rollbackDto = new OrderDto(1L, 1L, "ROLLBACK", "INVENTORY", List.of(orderItemDto));
        orderManageService.confirm(rollbackDto);
        assertThat(customer.getAmountReserved()).isEqualByComparingTo(initialReserved);
        assertThat(customer.getAmountAvailable()).isEqualByComparingTo(initialAvailable);
    }

    @Test
    void largeValueOrderTest() {
        // Arrange
        BigDecimal largePrice = new BigDecimal("5000000000.00"); // > Integer.MAX_VALUE
        OrderDto.OrderItemDto orderItemDto =
                new OrderDto.OrderItemDto(1L, "productId", 1, largePrice);
        OrderDto orderDto = new OrderDto(1L, 1L, "NEW", null, List.of(orderItemDto));
        Customer customer = TestData.getCustomer();
        // Give customer enough funds
        customer.setAmountAvailable(new BigDecimal("6000000000.00"));
        BigDecimal initialAvailable = customer.getAmountAvailable();
        BigDecimal initialReserved = customer.getAmountReserved();

        given(customerRepository.findById(orderDto.customerId())).willReturn(Optional.of(customer));
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // Reserve
        OrderDto reservedOrder = orderManageService.reserve(orderDto);
        assertThat(reservedOrder.status()).isEqualTo("ACCEPT");
        assertThat(customer.getAmountReserved())
                .isEqualByComparingTo(initialReserved.add(largePrice));
        assertThat(customer.getAmountAvailable())
                .isEqualByComparingTo(initialAvailable.subtract(largePrice));
    }
}
