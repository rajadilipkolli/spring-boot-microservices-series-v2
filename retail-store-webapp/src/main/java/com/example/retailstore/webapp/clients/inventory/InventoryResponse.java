package com.example.retailstore.webapp.clients.inventory;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InventoryResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
        @NotBlank String productCode,
        @NotNull @PositiveOrZero Integer availableQuantity,
        Integer reservedItems) {
    public InventoryUpdateRequest createInventoryUpdateRequest() {
        return InventoryUpdateRequest.fromInventoryResponse(this);
    }
}
