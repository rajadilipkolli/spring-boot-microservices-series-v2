/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.model.response;

public record InventoryResponse(
        Long id, String productCode, Integer availableQuantity, Integer reservedItems) {}
