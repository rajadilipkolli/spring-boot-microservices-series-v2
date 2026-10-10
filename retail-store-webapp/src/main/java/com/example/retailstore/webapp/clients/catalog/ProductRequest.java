/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
 * </p>
 ***/
package com.example.retailstore.webapp.clients.catalog;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Product code can't be blank") String productCode,
        @NotBlank(message = "Product name can't be blank") String productName,
        String description,
        String imageUrl,

        @NotNull(message = "Price cannot be null") @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal price) {}
