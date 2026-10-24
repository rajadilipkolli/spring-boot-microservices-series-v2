/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.common.dto.OrderDto;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentOrderManageServiceTest {

    @Mock private PaymentOperationEngine operationEngine;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @InjectMocks private PaymentOrderManageService orderManageService;

    /** Verifies that confirming an order delegates a CAPTURE settlement to the engine. */
    @Test
    void confirmDelegatesToEngineCapture() {
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "p1", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "CONFIRMED", null, List.of(item), "att1");

        orderManageService.confirm(orderDto);

        verify(operationEngine, times(1)).processSettlement(orderDto, "CAPTURE");
    }

    /** Verifies that rolling back an order delegates a RELEASE settlement to the engine. */
    @Test
    void rollbackDelegatesToEngineRelease() {
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "p1", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "ROLLBACK", "INVENTORY", List.of(item), "att1");

        orderManageService.confirm(orderDto);

        verify(operationEngine, times(1)).processSettlement(orderDto, "RELEASE");
    }

    /** Verifies that reservation delegates to the engine and returns its payment outcome. */
    @Test
    void reserveDelegatesToEngine() {
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "p1", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 1L, "NEW", null, List.of(item), "att1");
        OrderDto acceptedDto = orderDto.withStatus("ACCEPT").withSource("PAYMENT");

        given(operationEngine.processReserve(any(OrderDto.class))).willReturn(acceptedDto);

        OrderDto result = orderManageService.reserve(orderDto);

        assertThat(result.status()).isEqualTo("ACCEPT");
        assertThat(result.source()).isEqualTo("PAYMENT");
        verify(operationEngine, times(1)).processReserve(orderDto);
    }
}
