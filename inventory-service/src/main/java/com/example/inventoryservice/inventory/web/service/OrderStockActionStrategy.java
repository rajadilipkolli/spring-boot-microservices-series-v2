/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.OrderDto;

/** Strategy interface for applying stock updates based on order status during confirmation. */
interface OrderStockActionStrategy {

    boolean supports(String status, String source);

    void apply(Inventory inventory, OrderDto.OrderItemDto item);
}
