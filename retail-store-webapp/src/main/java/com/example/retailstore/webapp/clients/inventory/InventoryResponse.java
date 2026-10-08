package com.example.retailstore.webapp.clients.inventory;

import com.fasterxml.jackson.annotation.JsonFormat;

public record InventoryResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
        String productCode,
        Integer availableQuantity,
        Integer reservedItems) {
    public InventoryUpdateRequest createInventoryUpdateRequest() {
        return InventoryUpdateRequest.fromInventoryResponse(this);
    }
}
