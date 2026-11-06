/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.PagedResult;
import com.example.common.logging.Loggable;
import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.domain.models.InventoryResponse;
import com.example.inventoryservice.inventory.exception.ProductAlreadyExistsException;
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing inventory queries (reads via jOOQ) and commands (writes via Spring Data JPA)
 * adhering to SOLID principles and CQRS separation with custom fallback handling.
 */
@Service
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Loggable
class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final InventoryJOOQRepository inventoryJOOQRepository;
    private final InventoryRetryHandler retryHandler;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryMapper inventoryMapper,
            InventoryJOOQRepository inventoryJOOQRepository,
            InventoryRetryHandler retryHandler) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryMapper = inventoryMapper;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
        this.retryHandler = retryHandler;
    }

    /**
     * Returns a sorted page of active inventory with pagination metadata via jOOQ read. Persistence
     * failures and invalid sort properties propagate.
     *
     * @param pageNo zero-based page index; the result reports a one-based page number
     * @param sortDir {@code asc}, ignoring case, for ascending order; otherwise descending
     * @throws IllegalArgumentException if the page index is negative or the page size is not
     *     positive
     */
    public PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir) {

        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<InventoryResponse> page =
                inventoryJOOQRepository
                        .findAllAndDeletedFalse(pageable)
                        .map(inventoryMapper::toResponse);
        return new PagedResult<>(page);
    }

    /**
     * Creates inventory or restores deleted inventory with the requested available quantity,
     * preserving its ID and reserved quantity. Other persistence failures propagate.
     *
     * @return the saved inventory
     * @throws ProductAlreadyExistsException if active inventory already exists for the code, or an
     *     integrity failure is followed by finding active inventory for that code
     * @throws DataIntegrityViolationException if saving fails an integrity constraint and the
     *     product code has no active inventory
     */
    public Inventory saveInventory(InventoryRequest inventoryRequest) {
        return retryHandler.executeWithRetry(() -> saveInventoryAttempt(inventoryRequest));
    }

    private Inventory saveInventoryAttempt(InventoryRequest inventoryRequest) {
        // Read via jOOQ to check existing record
        Optional<InventoryRecord> existingRecordOpt =
                inventoryJOOQRepository.findByProductCode(inventoryRequest.productCode());

        Inventory inventory;
        if (existingRecordOpt.isPresent()) {
            InventoryRecord record = existingRecordOpt.get();
            if (Boolean.FALSE.equals(record.getDeleted())) {
                throw new ProductAlreadyExistsException(inventoryRequest.productCode());
            }
            inventory = inventoryMapper.toEntity(record);
            inventoryMapper.updateInventoryFromRequest(inventoryRequest, inventory);
            inventory.setDeleted(false);
        } else {
            inventory = inventoryMapper.toEntity(inventoryRequest);
        }

        try {
            return saveWithCustomFallback(inventory);
        } catch (DataIntegrityViolationException ex) {
            if (inventoryJOOQRepository.existsByProductCodeAndDeletedFalse(
                    inventoryRequest.productCode())) {
                throw new ProductAlreadyExistsException(inventoryRequest.productCode());
            }
            throw ex;
        }
    }

    /**
     * Soft deletes active inventory without changing available or reserved quantities. Does nothing
     * if absent or already deleted; persistence failures propagate.
     */
    public void deleteInventoryById(Long id) {
        retryHandler.executeWithRetry(() -> {
                    deleteInventoryByIdAttempt(id);
                    return null;
                });
    }

    private void deleteInventoryByIdAttempt(Long id) {
        // Check active existence via jOOQ read
        inventoryJOOQRepository
                .findByIdAndDeletedFalse(id)
                .ifPresent(
                        record -> {
                            Inventory inventory = inventoryMapper.toEntity(record);
                            inventory.markDeleted();
                            saveWithCustomFallback(inventory);
                        });
    }

    /** Applies the request to the entity, preserving reserved quantity, and saves it. */
    public Inventory updateInventory(Inventory inventory, InventoryRequest inventoryRequest) {
        this.inventoryMapper.updateInventoryFromRequest(inventoryRequest, inventory);
        return saveWithCustomFallback(inventory);
    }

    /** Returns active inventory by exact product code, or empty if absent or deleted. */
    public Optional<InventoryResponse> findInventoryByProductCode(String productCode) {
        return this.inventoryJOOQRepository
                .findByProductCodeAndDeletedFalse(productCode)
                .map(inventoryMapper::toResponse);
    }

    /** Returns a sorted page of active inventory matching the supplied codes via jOOQ read. */
    public PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> productCodes, int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<InventoryResponse> page =
                this.inventoryJOOQRepository
                        .findByProductCodeInAndDeletedFalse(productCodes, pageable)
                        .map(inventoryMapper::toResponse);
        return new PagedResult<>(page);
    }

    /**
     * Updates active inventory with the request's available quantity, preserving reserved quantity.
     * Persistence failures propagate.
     *
     * @return the saved inventory response, or empty if the ID is absent or soft deleted
     */
    public Optional<InventoryResponse> updateInventoryById(
            Long id, InventoryRequest inventoryRequest) {
        return retryHandler.executeWithRetry(() -> updateInventoryByIdAttempt(id, inventoryRequest));
    }

    private Optional<InventoryResponse> updateInventoryByIdAttempt(
            Long id, InventoryRequest inventoryRequest) {
        return inventoryJOOQRepository.findByIdAndDeletedFalse(id).map(record -> {
            Inventory entity = inventoryMapper.toEntity(record);
            inventoryMapper.updateInventoryFromRequest(inventoryRequest, entity);
            return inventoryMapper.toResponse(inventoryRepository.save(entity));
        });
    }

    /**
     * Updates active inventory with the request, preserving reserved quantity. Persistence failures
     * propagate.
     *
     * @param productCode code used to find the inventory
     * @return the saved inventory response, or empty if the code is absent or soft deleted
     */
    public Optional<InventoryResponse> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest) {
        return retryHandler.executeWithRetry(() -> updateInventoryByProductCodeAttempt(productCode, inventoryRequest));
    }

    private Optional<InventoryResponse> updateInventoryByProductCodeAttempt(
            String productCode, InventoryRequest inventoryRequest) {
        return inventoryJOOQRepository.findByProductCodeAndDeletedFalse(productCode).map(record -> {
            Inventory entity = inventoryMapper.toEntity(record);
            inventoryMapper.updateInventoryFromRequest(inventoryRequest, entity);
            return inventoryMapper.toResponse(inventoryRepository.save(entity));
        });
    }

    /** Returns whether active inventory exists for the ID via jOOQ read. */
    public boolean existsByIdAndDeletedFalse(Long id) {
        return inventoryJOOQRepository.existsByIdAndDeletedFalse(id);
    }

    /**
     * Saves inventory with retries on optimistic locking failure. If possible, refreshes its version
     * from the database after a conflict. Unresolved persistence failures propagate.
     */
    private Inventory saveWithCustomFallback(Inventory inventory) {
        return inventoryRepository.save(inventory);
    }
}
