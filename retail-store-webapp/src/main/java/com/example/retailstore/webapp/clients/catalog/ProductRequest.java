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
        String productName,
        String description,
        String imageUrl,
        @Positive Double price) {}
