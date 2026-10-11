/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.inventoryservice.inventory.ProductManageService;
import com.example.inventoryservice.inventory.domain.models.ProductDto;
import com.example.inventoryservice.inventory.utils.logging.Loggable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Loggable
class ProductManageServiceImpl implements ProductManageService {

    private final InventoryRepository inventoryRepository;
    private final InventoryJOOQRepository inventoryJOOQRepository;

    public ProductManageServiceImpl(
            InventoryRepository inventoryRepository,
            InventoryJOOQRepository inventoryJOOQRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
    }

    @Override
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
