/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.model.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ProductDto(
        @NotBlank(message = "Product code can't be blank") String code,
        String productName,
        String description,
        @Positive Double price) {}
