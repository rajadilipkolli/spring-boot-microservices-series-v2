/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.ProductDto;
import com.example.common.logging.Loggable;
import com.example.inventoryservice.inventory.ProductManageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Loggable
class ProductManageServiceImpl implements ProductManageService {

    private final InventoryRepository inventoryRepository;
    private final InventoryJOOQRepository inventoryJOOQRepository;

    /** Creates the product handler with repositories for duplicate checks and inventory writes. */
    public ProductManageServiceImpl(
            InventoryRepository inventoryRepository,
            InventoryJOOQRepository inventoryJOOQRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
    }

    /**
     * Creates inventory for a new product code, ignoring existing or concurrently created codes.
     *
     * <p>Integrity failures propagate when the product code is still absent after the failed
     * insert.
     */
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
