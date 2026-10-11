/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory;

import com.example.inventoryservice.inventory.domain.models.OrderDto;

public interface InventoryOrderManageService {

    OrderDto reserve(OrderDto orderDto);

    void confirm(OrderDto orderDto);
}
