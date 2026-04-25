/***
<p>
    Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import com.example.inventoryservice.config.logging.Loggable;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.payload.ProductDto;
import com.example.inventoryservice.repositories.InventoryJOOQRepository;
import com.example.inventoryservice.repositories.InventoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Loggable
public class ProductManageService {

    private final InventoryRepository inventoryRepository;
    private final InventoryJOOQRepository inventoryJOOQRepository;

    public ProductManageService(
            InventoryRepository inventoryRepository,
            InventoryJOOQRepository inventoryJOOQRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
    }

    public void manage(ProductDto productDto) {
        String productCode = productDto.code();
        // avoid inserting duplicate inventory records when retries or duplicate events arrive
        if (this.inventoryJOOQRepository.existsByProductCode(productCode)) {
            return;
        }

        Inventory inventory = new Inventory().setProductCode(productCode);
        try {
            this.inventoryRepository.save(inventory);
        } catch (DataIntegrityViolationException ex) {
            if (!this.inventoryJOOQRepository.existsByProductCode(productCode)) {
                throw ex;
            }
            // Another thread created the same product_code concurrently; ignore to keep
            // idempotency. Logging is handled by the @Loggable aspect.
        }
    }
}
