/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class OrderDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private static final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void whenItemIsNull_thenValidationFails() {
        List<OrderDto.OrderItemDto> items = new ArrayList<>();
        items.add(null);
        OrderDto orderDto = new OrderDto(1L, 100L, "NEW", "WEB", items);

        Set<ConstraintViolation<OrderDto>> violations = validator.validate(orderDto);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be null");
    }

    @Test
    void whenEmptyItems_thenValidationFails() {
        OrderDto orderDto = new OrderDto(1L, 100L, "NEW", "WEB", List.of());
        Set<ConstraintViolation<OrderDto>> violations = validator.validate(orderDto);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Order without items not valid");
    }

    @Test
    void whenNegativeCustomerId_thenValidationFails() {
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "P01", 1, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, -5L, "NEW", "WEB", List.of(item));
        Set<ConstraintViolation<OrderDto>> violations = validator.validate(orderDto);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("CustomerId should be positive");
    }

    @Test
    void orderItemDtoGetPriceCalculatesCorrectly() {
        OrderDto.OrderItemDto item =
                new OrderDto.OrderItemDto(1L, "P01", 3, new BigDecimal("12.345"));
        assertThat(item.getPrice()).isEqualByComparingTo(new BigDecimal("37.04"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsNumericAndStringIdsWithoutPrecisionLoss(boolean stringIds) {
        String id = stringIds ? "\"9007199254740993\"" : "9007199254740993";
        String json =
                """
                {"orderId":%s,"customerId":%s,"status":"NEW","source":"WEB",
                 "items":[{"itemId":%s,"productId":"P001","quantity":1,"productPrice":10}]}
                """
                        .formatted(id, id, id);

        OrderDto order = jsonMapper.readValue(json, OrderDto.class);
        assertThat(order.orderId()).isEqualTo(9007199254740993L);
        assertThat(order.customerId()).isEqualTo(9007199254740993L);
        assertThat(order.items().getFirst().itemId()).isEqualTo(9007199254740993L);
    }

    @Test
    void witherMethodsAndAttemptIdPreserved() {
        var order = new OrderDto(1L, 2L, "NEW", "SOURCE_A", List.of(), "attempt-1");
        assertThat(order.withSource("SOURCE_A")).isSameAs(order);
        assertThat(order.withStatus("NEW")).isSameAs(order);
        assertThat(order.withStatusAndSource("NEW", "SOURCE_A")).isSameAs(order);

        var changed = order.withSource("SOURCE_B").withStatus("ACCEPT").withAttemptId("attempt-2");
        assertThat(changed.source()).isEqualTo("SOURCE_B");
        assertThat(changed.status()).isEqualTo("ACCEPT");
        assertThat(changed.attemptId()).isEqualTo("attempt-2");

        var statusAndSource = order.withStatusAndSource("REJECT", "SOURCE_C");
        assertThat(statusAndSource.status()).isEqualTo("REJECT");
        assertThat(statusAndSource.source()).isEqualTo("SOURCE_C");
        assertThat(statusAndSource.attemptId()).isEqualTo("attempt-1");
    }

    @Test
    void jsonSerializationFormatMatchesStringIds() {
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "Product A", 2, BigDecimal.TEN);
        OrderDto order = new OrderDto(1L, 123L, "NEW", "TEST_SOURCE", List.of(item));

        String json = jsonMapper.writeValueAsString(order);
        assertThat(json).contains("\"orderId\":\"1\"");
        assertThat(json).contains("\"customerId\":\"123\"");
        assertThat(json).contains("\"itemId\":\"1\"");
    }
}
