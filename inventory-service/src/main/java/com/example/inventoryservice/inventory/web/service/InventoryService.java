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
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Loggable
class InventoryService {

    private static final SecureRandom RAND = new SecureRandom();
    private static final int DEFAULT_GENERATION_BATCH_SIZE = 101;

    private final Set<String> processedIdempotencyKeys = ConcurrentHashMap.newKeySet();

    private final InventoryRepository inventoryRepository;

    private final InventoryMapper inventoryMapper;

    private final InventoryJOOQRepository inventoryJOOQRepository;

    private final InventoryService self;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryMapper inventoryMapper,
            InventoryJOOQRepository inventoryJOOQRepository,
            @Lazy InventoryService self) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryMapper = inventoryMapper;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
        this.self = self;
    }

    /**
     * Returns a sorted page of active inventory with pagination metadata. Persistence failures and
     * invalid sort properties propagate.
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
    @Transactional
    public Inventory saveInventory(InventoryRequest inventoryRequest) {
        Inventory inventory =
                inventoryRepository
                        .findByProductCode(inventoryRequest.productCode())
                        .map(
                                retained -> {
                                    if (!retained.isDeleted()) {
                                        throw new ProductAlreadyExistsException(
                                                inventoryRequest.productCode());
                                    }
                                    inventoryMapper.updateInventoryFromRequest(
                                            inventoryRequest, retained);
                                    return retained.setDeleted(false);
                                })
                        .orElseGet(() -> inventoryMapper.toEntity(inventoryRequest));
        try {
            return inventoryRepository.save(inventory);
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
    @Transactional
    public void deleteInventoryById(Long id) {
        inventoryRepository
                .findByIdAndDeletedFalse(id)
                .ifPresent(
                        inventory -> {
                            inventory.markDeleted();
                            inventoryRepository.save(inventory);
                        });
    }

    @Transactional
    public Inventory updateInventory(Inventory inventory, InventoryRequest inventoryRequest) {
        this.inventoryMapper.updateInventoryFromRequest(inventoryRequest, inventory);
        return inventoryRepository.save(inventory);
    }

    /**
     * Returns active inventory by exact product code, or empty if absent or deleted; persistence
     * failures propagate.
     */
    public Optional<Inventory> findInventoryByProductCode(String productCode) {
        return this.inventoryRepository.findByProductCodeAndDeletedFalse(productCode);
    }

    /**
     * Returns a sorted page of active inventory matching the supplied codes, with pagination
     * metadata. Missing or deleted products are omitted. Persistence failures and invalid sort
     * properties propagate.
     *
     * @param pageNo zero-based page index; the result reports a one-based page number
     * @param sortDir {@code asc}, ignoring case, for ascending order; otherwise descending
     * @throws IllegalArgumentException if the page index is negative or the page size is not
     *     positive
     */
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
     * Assigns random available quantities from 1 through 10,000 to active inventory with codes
     * {@code ProductCode_<key>_<index>}, visiting indexes from zero through batch size minus one.
     * Missing or deleted inventory is skipped; reserved quantities are preserved.
     *
     * <p>Batch size is validated before claiming the key. Repeated keys are ignored for this
     * service instance's lifetime, even after a partial failure. Optimistic locking failures are
     * ignored per item; other persistence failures propagate and stop the batch.
     *
     * @param idempotencyKey non-null key identifying the batch and preventing repeated processing
     * @param batchSize number of codes to visit, or {@code null} for 101
     * @throws IllegalArgumentException if the batch size is outside 1 through 10,000
     * @throws NullPointerException if the idempotency key is null
     */
    public void updateGeneratedInventory(String idempotencyKey, Integer batchSize) {
        int resolvedBatchSize = batchSize != null ? batchSize : DEFAULT_GENERATION_BATCH_SIZE;
        if (resolvedBatchSize < 1 || resolvedBatchSize > 10_000) {
            throw new IllegalArgumentException("Batch size must be between 1 and 10,000");
        }

        if (!processedIdempotencyKeys.add(idempotencyKey)) {
            return;
        }

        IntStream.range(0, resolvedBatchSize)
                .forEach(
                        operand -> {
                            try {
                                int randomQuantity = RAND.nextInt(10_000) + 1;
                                Optional<Inventory> inventoryByProductCode =
                                        findInventoryByProductCode(
                                                "ProductCode_" + idempotencyKey + "_" + operand);
                                inventoryByProductCode.ifPresent(
                                        inventoryFromDB ->
                                                self.updateInventory(
                                                        inventoryFromDB,
                                                        new InventoryRequest(
                                                                "ProductCode_"
                                                                        + idempotencyKey
                                                                        + "_"
                                                                        + operand,
                                                                randomQuantity)));
                            } catch (OptimisticLockingFailureException e) {
                                // Ignore optimistic locking failures when concurrently updating
                                // random inventory
                            }
                        });
    }

    /**
     * Updates active inventory with the request's available quantity, preserving reserved quantity.
     * Persistence failures propagate.
     *
     * @return the saved inventory, or empty if the ID is absent or soft deleted
     */
    @Transactional
    public Optional<Inventory> updateInventoryById(Long id, InventoryRequest inventoryRequest) {
        return inventoryRepository
                .findByIdAndDeletedFalse(id)
                .map(inventoryFromDB -> self.updateInventory(inventoryFromDB, inventoryRequest));
    }

    @Transactional
    public Optional<Inventory> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest) {
        return findInventoryByProductCode(productCode)
                .map(inventoryFromDB -> self.updateInventory(inventoryFromDB, inventoryRequest));
    }

    /** Returns whether active inventory exists for the ID; persistence failures propagate. */
    public boolean existsByIdAndDeletedFalse(Long id) {
        return inventoryJOOQRepository.existsByIdAndDeletedFalse(id);
    }
}
