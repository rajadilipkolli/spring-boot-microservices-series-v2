/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Shared product payload transferred between catalog-service outbox and inventory-service.
 *
 * @param code unique product identifier
 * @param productName human-readable name of the product
 * @param description detailed description of the product
 * @param price unit price formatted with standard two decimal digits
 */
public record ProductDto(
        @NotBlank(message = "Product code can't be blank") String code,
        String productName,
        String description,
        @Positive
                @Digits(integer = 17, fraction = 2)
                @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
                BigDecimal price)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public ProductDto {
        if (price != null && price.scale() < 2) {
            price = price.setScale(2);
        }
    }
}
