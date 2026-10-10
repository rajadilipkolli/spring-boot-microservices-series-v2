/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.events;

import com.example.inventoryservice.model.payload.OrderDto;
import org.springframework.modulith.events.Externalized;

@Externalized("stock-orders::#{#this.order().orderId()}")
public record InventoryProcessedEvent(OrderDto order) {}
