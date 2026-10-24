/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * @param price unit price, normalized to at least two decimal places when non-null
 * @param deleted whether the product is soft deleted
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
        @NotBlank(message = "Product code can't be blank") String code,
        String productName,
        String description,
        @Positive
                @Digits(integer = 17, fraction = 2)
                @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
                BigDecimal price,
        Boolean deleted)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * Creates a product payload with deleted defaulting to false.
     *
     * @param code unique product identifier
     * @param productName human-readable name of the product
     * @param description detailed description of the product
     * @param price unit price
     */
    public ProductDto(String code, String productName, String description, BigDecimal price) {
        this(code, productName, description, price, false);
    }

    /**
     * Returns true if the product is deleted, false if deleted is false or null.
     */
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * Creates a product payload, padding prices with fewer than two decimal places with trailing
     * zeros. Null prices and higher precision are preserved; validation constraints are not checked
     * here.
     */
    public ProductDto {
        if (price != null && price.scale() < 2) {
            if ((long) price.precision() - price.scale() > 17) {
                throw new IllegalArgumentException("Price must have at most 17 integer digits");
            }
            price = price.setScale(2);
        }
    }
}
