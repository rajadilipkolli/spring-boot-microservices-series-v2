/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderservice.model.Address;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenItemIsNull_thenValidationFails() {
        List<OrderItemRequest> items = new ArrayList<>();
        items.add(null);
        Address address = new Address("Line 1", "Line 2", "City", "State", "ZipCode", "Country");
        OrderRequest orderRequest = new OrderRequest(100L, items, address);

        Set<ConstraintViolation<OrderRequest>> violations = validator.validate(orderRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be null");
    }
}
