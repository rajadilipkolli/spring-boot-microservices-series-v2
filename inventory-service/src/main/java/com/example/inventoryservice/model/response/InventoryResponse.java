/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;

public record InventoryResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
        String productCode,
        Integer availableQuantity,
        Integer reservedItems) {}
