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
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProductDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

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
    void rejectsBlankCode() {
        ProductDto dto = new ProductDto("  ", "Name", "Desc", BigDecimal.valueOf(10.00));
        Set<ConstraintViolation<ProductDto>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
    }

    @Test
    void rejectsNonPositivePrice() {
        ProductDto zeroPrice = new ProductDto("CODE1", "Name", "Desc", BigDecimal.ZERO);
        Set<ConstraintViolation<ProductDto>> violationsZero = validator.validate(zeroPrice);
        assertThat(violationsZero).anyMatch(v -> v.getPropertyPath().toString().equals("price"));

        ProductDto negativePrice =
                new ProductDto("CODE1", "Name", "Desc", BigDecimal.valueOf(-1.00));
        Set<ConstraintViolation<ProductDto>> violationsNegative = validator.validate(negativePrice);
        assertThat(violationsNegative)
                .anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }

    @Test
    void rejectsPriceWithMoreThanTwoFractionDigits() {
        ProductDto dto = new ProductDto("CODE1", "Name", "Desc", new BigDecimal("10.999"));
        Set<ConstraintViolation<ProductDto>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }

    @Test
    void acceptsValidRecord() {
        ProductDto dto =
                new ProductDto("CODE1", "Product Name", "Description", new BigDecimal("19.99"));
        Set<ConstraintViolation<ProductDto>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }
}
