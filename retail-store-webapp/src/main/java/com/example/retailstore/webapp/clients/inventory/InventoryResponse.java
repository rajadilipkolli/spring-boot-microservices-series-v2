package com.example.retailstore.webapp.clients.inventory;

public record InventoryResponse(Long id, String productCode, Integer availableQuantity, Integer reservedItems) {
    public InventoryUpdateRequest createInventoryUpdateRequest() {
        return InventoryUpdateRequest.fromInventoryResponse(this);
    }
}
