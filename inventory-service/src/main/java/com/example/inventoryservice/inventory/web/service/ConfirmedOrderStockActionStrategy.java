/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.OrderDto;
import org.springframework.stereotype.Component;

@Component
class ConfirmedOrderStockActionStrategy implements OrderStockActionStrategy {

    /** Matches the case-sensitive status {@code CONFIRMED}, regardless of source. */
    @Override
    public boolean supports(String status, String source) {
        return "CONFIRMED".equals(status);
    }

    /** Subtracts the item quantity from reserved stock. */
    @Override
    public void apply(Inventory inventory, OrderDto.OrderItemDto item) {
        inventory.setReservedItems(inventory.getReservedItems() - item.quantity());
    }
}
