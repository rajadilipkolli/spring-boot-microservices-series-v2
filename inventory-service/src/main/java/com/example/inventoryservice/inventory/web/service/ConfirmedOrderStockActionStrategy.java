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

    @Override
    public boolean supports(String status, String source) {
        return "CONFIRMED".equals(status);
    }

    @Override
    public void apply(Inventory inventory, OrderDto.OrderItemDto item) {
        inventory.setReservedItems(inventory.getReservedItems() - item.quantity());
    }
}
