/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory;

import com.example.inventoryservice.inventory.domain.models.ProductDto;

public interface ProductManageService {
    /**
     * Creates inventory for the product code if absent, tolerating duplicate creation events.
     *
     * @param productDto product whose code identifies the inventory record
     */
    void manage(ProductDto productDto);
}
