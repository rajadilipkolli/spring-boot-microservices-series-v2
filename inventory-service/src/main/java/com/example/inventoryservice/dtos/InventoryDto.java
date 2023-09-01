/***
<p>
    Licensed under MIT License Copyright (c) 2021-2022 Raja Kolli.
</p>
***/

package com.example.inventoryservice.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;

public record InventoryDto(
        @NotBlank(message = "ProductCode can't be blank") String productCode,
        @PositiveOrZero(message = "Quantity can't be negative") Integer availableQuantity)
        implements Serializable {}
