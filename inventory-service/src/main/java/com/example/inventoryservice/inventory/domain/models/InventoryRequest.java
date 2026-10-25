/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.domain.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;

@Schema(description = "Inventory creation or update request")
public record InventoryRequest(
        @Schema(description = "Product code", example = "P100")
                @NotBlank(message = "ProductCode can't be blank")
                String productCode,
        @Schema(description = "Available quantity", example = "100")
                @PositiveOrZero(message = "Quantity can't be negative")
                Integer availableQuantity)
        implements Serializable {}
