/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "Inventory availability response")
public record InventoryResponse(
        @Schema(description = "Product code", example = "P100") String productCode,
        @Schema(description = "Available quantity in stock", example = "50")
                Integer availableQuantity)
        implements Serializable {}
