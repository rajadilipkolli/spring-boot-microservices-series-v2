/***
 * <p>
 * Licensed under MIT License Copyright (c) 2024 Raja Kolli.
 * </p>
 ***/
package com.example.retailstore.webapp.clients.catalog;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ProductResponse(
        Long id,
        String productCode,
        String productName,
        String description,
        String imageUrl,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00") Double price,
        boolean inStock) {}
