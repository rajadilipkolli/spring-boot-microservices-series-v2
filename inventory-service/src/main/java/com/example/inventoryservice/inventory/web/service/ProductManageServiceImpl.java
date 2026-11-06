/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.ProductDto;
import com.example.common.logging.Loggable;
import com.example.inventoryservice.inventory.ProductManageService;
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Loggable
class ProductManageServiceImpl implements ProductManageService {

    private static final Logger log = LoggerFactory.getLogger(ProductManageServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryJOOQRepository inventoryJOOQRepository;
    private final InventoryMapper inventoryMapper;
    private final InventoryRetryHandler retryHandler;

    /** Creates the product handler with repositories for reads (jOOQ) and writes (JPA). */
    public ProductManageServiceImpl(
            InventoryRepository inventoryRepository,
            InventoryJOOQRepository inventoryJOOQRepository,
            InventoryMapper inventoryMapper,
            InventoryRetryHandler retryHandler) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
        this.inventoryMapper = inventoryMapper;
        this.retryHandler = retryHandler;
    }

    /**
     * Creates inventory for a new product code, ignoring existing or concurrently created codes.
     * Existing codes include deleted inventory, which is not restored. A deleted product DTO soft
     * deletes active inventory; absent or already deleted inventory is left unchanged.
     *
     * <p>Reads are executed via jOOQ and writes via Spring Data JPA.
     *
     * <p>Integrity failures propagate when the product code is still absent after the failed
     * insert. Other persistence failures, including deletion failures, propagate.
     */
    @Override
    @Transactional
    public void manage(ProductDto productDto) {
        String productCode = productDto.code();
        if (productDto.isDeleted()) {
            // Read via jOOQ
            Optional<InventoryRecord> recordOpt =
                    inventoryJOOQRepository.findByProductCode(productCode);
            if (recordOpt.isPresent()) {
                InventoryRecord record = recordOpt.get();
                if (Boolean.TRUE.equals(record.getDeleted())) {
                    log.info("Inventory already deleted for productCode: {}", productCode);
                    return;
                }
                Inventory inventory = inventoryMapper.toEntity(record);
                inventory.markDeleted();
                retryHandler.executeWithRetry(() -> inventoryRepository.save(inventory));
                log.info("Soft deleted inventory for productCode: {}", productCode);
            } else {
                log.info("No inventory found to soft delete for productCode: {}", productCode);
            }
            return;
        }

        // Avoid inserting duplicate inventory records when retries or duplicate events arrive (read
        // via jOOQ)
        if (this.inventoryJOOQRepository.findByProductCode(productCode).isPresent()) {
            return;
        }

        Inventory inventory = new Inventory().setProductCode(productCode);
        try {
            retryHandler.executeWithRetry(() -> this.inventoryRepository.save(inventory));
        } catch (DataIntegrityViolationException ex) {
            if (this.inventoryJOOQRepository.findByProductCode(productCode).isEmpty()) {
                throw ex;
            }
            // Another thread created the same product_code concurrently; ignore to keep
            // idempotency. Logging is handled by the @Loggable aspect.
        }
    }
}
