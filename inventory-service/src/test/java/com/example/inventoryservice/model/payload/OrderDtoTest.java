/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.model.payload;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class OrderDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void whenItemIsNull_thenValidationFails() {
        List<OrderItemDto> items = new ArrayList<>();
        items.add(null);
        OrderDto orderDto = new OrderDto(1L, 100L, "NEW", "WEB", items);

        Set<ConstraintViolation<OrderDto>> violations = validator.validate(orderDto);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be null");
    }

    /**
     * Verifies that numeric and string JSON IDs above JavaScript's safe integer limit retain their
     * exact long values.
     *
     * @param stringIds whether the payload encodes IDs as strings instead of numbers
     */
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

        OrderDto order = JsonMapper.builder().build().readValue(json, OrderDto.class);
        assertThat(order.orderId()).isEqualTo(9007199254740993L);
        assertThat(order.customerId()).isEqualTo(9007199254740993L);
        assertThat(order.items().getFirst().itemId()).isEqualTo(9007199254740993L);
    }
}
