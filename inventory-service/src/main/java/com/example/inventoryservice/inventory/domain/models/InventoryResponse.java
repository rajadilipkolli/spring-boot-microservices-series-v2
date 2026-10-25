/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.domain.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Inventory item details response")
public record InventoryResponse(
        @Schema(type = "string", description = "Inventory ID", example = "1")
                @JsonFormat(shape = JsonFormat.Shape.STRING)
                Long id,
        @Schema(description = "Product code", example = "P100") String productCode,
        @Schema(description = "Available quantity in stock", example = "50")
                Integer availableQuantity,
        @Schema(description = "Reserved quantity", example = "5") Integer reservedItems) {}
