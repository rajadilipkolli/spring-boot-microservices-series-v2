/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.events;

import com.example.inventoryservice.inventory.model.payload.OrderDto;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.modulith.events.Externalized;

@Externalized("stock-orders")
public record InventoryProcessedEvent(@JsonValue OrderDto order) {
    @JsonCreator
    public InventoryProcessedEvent {}
}
