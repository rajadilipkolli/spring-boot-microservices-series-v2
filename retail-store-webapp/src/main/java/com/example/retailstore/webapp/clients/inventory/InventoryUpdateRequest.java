package com.example.retailstore.webapp.clients.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InventoryUpdateRequest(
        @NotBlank String productCode,
        @NotNull @PositiveOrZero Integer availableQuantity) {

    public static InventoryUpdateRequest fromInventoryResponse(InventoryResponse inventoryResponse) {
        return new InventoryUpdateRequest(inventoryResponse.productCode(), inventoryResponse.availableQuantity());
    }
}
