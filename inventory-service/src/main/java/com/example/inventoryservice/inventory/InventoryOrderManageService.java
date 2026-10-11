/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory;

import com.example.inventoryservice.inventory.domain.models.OrderDto;

public interface InventoryOrderManageService {

    /**
     * Reserves stock for a {@code NEW} order and publishes the acceptance or rejection outcome.
     *
     * <p>Missing products, repeated product codes, or insufficient stock cause rejection without
     * stock changes. Orders with other statuses are returned unchanged without an event.
     *
     * @param orderDto order and item quantities to reserve
     * @return the order with the reservation status and inventory source, or the unchanged non-new
     *     order
     */
    OrderDto reserve(OrderDto orderDto);

    /**
     * Consumes reserved quantities for {@code CONFIRMED} orders or restores available stock for
     * {@code ROLLBACK} orders originating outside inventory.
     *
     * <p>Missing products are skipped; other statuses leave quantities unchanged. No event is
     * published by this operation.
     *
     * @param orderDto order whose reservation should be finalized or rolled back
     */
    void confirm(OrderDto orderDto);
}
