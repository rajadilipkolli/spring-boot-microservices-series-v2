/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.ProductDto;
import com.example.common.logging.Loggable;
import com.example.inventoryservice.inventory.ProductManageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Loggable
class ProductManageServiceImpl implements ProductManageService {

    private static final Logger log = LoggerFactory.getLogger(ProductManageServiceImpl.class);

    private final InventoryRepository inventoryRepository;

    /** Creates the product handler with a repository for duplicate checks and inventory writes. */
    public ProductManageServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Creates inventory for a new product code, ignoring existing or concurrently created codes.
     * Existing codes include deleted inventory, which is not restored. A deleted product DTO soft
     * deletes active inventory; absent or already deleted inventory is left unchanged.
     *
     * <p>Integrity failures propagate when the product code is still absent after the failed
     * insert. Other persistence failures, including deletion failures, propagate.
     */
    @Override
    public void manage(ProductDto productDto) {
        String productCode = productDto.code();
        if (productDto.isDeleted()) {
            inventoryRepository
                    .findByProductCode(productCode)
                    .ifPresentOrElse(
                            inventory -> {
                                if (inventory.isDeleted()) {
                                    log.info(
                                            "Inventory already deleted for productCode: {}",
                                            productCode);
                                    return;
                                }
                                inventory.markDeleted();
                                inventoryRepository.save(inventory);
                                log.info("Soft deleted inventory for productCode: {}", productCode);
                            },
                            () ->
                                    log.info(
                                            "No inventory found to soft delete for productCode: {}",
                                            productCode));
            return;
        }

        // avoid inserting duplicate inventory records when retries or duplicate events arrive
        if (this.inventoryRepository.findByProductCode(productCode).isPresent()) {
            return;
        }

        Inventory inventory = new Inventory().setProductCode(productCode);
        try {
            this.inventoryRepository.save(inventory);
        } catch (DataIntegrityViolationException ex) {
            if (this.inventoryRepository.findByProductCode(productCode).isEmpty()) {
                throw ex;
            }
            // Another thread created the same product_code concurrently; ignore to keep
            // idempotency. Logging is handled by the @Loggable aspect.
        }
    }
}
