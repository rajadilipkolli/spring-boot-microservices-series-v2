/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
 * </p>
 ***/
package com.example.retailstore.webapp.clients.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ProductRequest(
        @NotBlank(message = "Product code can't be blank") String productCode,
        @NotBlank(message = "Product name can't be blank") String productName,
        String description,
        String imageUrl,

        @Positive @jakarta.validation.constraints.Digits(integer = 17, fraction = 2)
        java.math.BigDecimal price) {}
