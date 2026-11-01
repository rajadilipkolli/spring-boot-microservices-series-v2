/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.OrderDto;

/** Strategy interface for applying stock updates based on order status during confirmation. */
interface OrderStockActionStrategy {

    /** Returns whether this strategy applies to the order status and source. */
    boolean supports(String status, String source);

    /** Adjusts inventory in place for the order item; the caller handles persistence. */
    void apply(Inventory inventory, OrderDto.OrderItemDto item);
}
