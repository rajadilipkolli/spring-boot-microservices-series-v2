/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.services.PaymentOrderManageService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KafkaListenerConfigTest {

    private PaymentOrderManageService paymentOrderManageService;
    private KafkaListenerConfig kafkaListenerConfig;

    @BeforeEach
    void setUp() {
        paymentOrderManageService = mock(PaymentOrderManageService.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        Validator validator;
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
        kafkaListenerConfig =
                new KafkaListenerConfig(paymentOrderManageService, jsonMapper, validator);
    }

    /**
     * Verifies that a NEW order containing a null item throws IllegalArgumentException and never
     * invokes reserve.
     */
    @Test
    void onEventWhenOrderContainsNullItemThrowsIllegalArgumentException() {
        String payload =
                """
                {"orderId":1,"customerId":1,"status":"NEW","source":"WEB","items":[null],"attemptId":"att-1"}
                """;

        assertThatThrownBy(() -> kafkaListenerConfig.onEvent(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload validation failed");

        verify(paymentOrderManageService, never()).reserve(any());
    }

    /** Verifies that a valid NEW order is passed to the payment reservation service. */
    @Test
    void onEventWhenOrderIsValidInvokesReserve() {
        String payload =
                """
                {"orderId":1,"customerId":1,"status":"NEW","source":"WEB","items":[{"itemId":1,"productId":"P1","quantity":1,"productPrice":10.0}],"attemptId":"att-1"}
                """;

        kafkaListenerConfig.onEvent(payload);

        verify(paymentOrderManageService).reserve(any(OrderDto.class));
    }
}
