package com.example.retailstore.webapp.clients.inventory;

public record InventoryUpdateRequest(String productCode, Integer availableQuantity) {

    public static InventoryUpdateRequest fromInventoryResponse(InventoryResponse inventoryResponse) {
        return new InventoryUpdateRequest(inventoryResponse.productCode(), inventoryResponse.availableQuantity());
    }
}
