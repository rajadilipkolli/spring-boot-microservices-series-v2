/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.OrderDto;
import com.example.inventoryservice.inventory.utils.AppConstants;
import org.springframework.stereotype.Component;

@Component
class RollbackOrderStockActionStrategy implements OrderStockActionStrategy {

    /** Matches {@code ROLLBACK} unless the source is {@code INVENTORY}, ignoring source case. */
    @Override
    public boolean supports(String status, String source) {
        return AppConstants.ROLLBACK.equals(status)
                && !AppConstants.SOURCE.equalsIgnoreCase(source);
    }

    /** Moves the item quantity from reserved to available stock. */
    @Override
    public void apply(Inventory inventory, OrderDto.OrderItemDto item) {
        int quantity = item.quantity();
        inventory
                .setReservedItems(inventory.getReservedItems() - quantity)
                .setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
    }
}
