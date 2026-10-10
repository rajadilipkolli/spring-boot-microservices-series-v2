/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.model.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ProductDto(
        @NotBlank(message = "Product code can't be blank") String code,
        String productName,
        String description,
        @Positive
                @Digits(integer = 17, fraction = 2)
                @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "0.00")
                BigDecimal price) {}
