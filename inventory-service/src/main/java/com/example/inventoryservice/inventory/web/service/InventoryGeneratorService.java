/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.logging.Loggable;
import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@Loggable
class InventoryGeneratorService {

    private static final SecureRandom RAND = new SecureRandom();
    private static final int DEFAULT_GENERATION_BATCH_SIZE = 101;

    private final Set<String> processedIdempotencyKeys = ConcurrentHashMap.newKeySet();
    private final InventoryService inventoryService;

    InventoryGeneratorService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Assigns random available quantities from 1 through 10,000 to active inventory with codes
     * {@code ProductCode_<key>_<index>}, visiting indexes from zero through batch size minus one.
     * Missing or deleted inventory is skipped; reserved quantities are preserved.
     *
     * <p>Batch size is validated before claiming the key. Repeated keys are ignored for this
     * service instance's lifetime. Optimistic locking failures are ignored per item; other
     * persistence failures propagate, remove the idempotency key to allow a retry, and stop the
     * batch.
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

        try {
            IntStream.range(0, resolvedBatchSize)
                    .forEach(
                            operand -> {
                                try {
                                    int randomQuantity = RAND.nextInt(10_000) + 1;
                                    String productCode =
                                            "ProductCode_" + idempotencyKey + "_" + operand;
                                    inventoryService.updateInventoryByProductCode(
                                            productCode,
                                            new InventoryRequest(productCode, randomQuantity));
                                } catch (OptimisticLockingFailureException e) {
                                    // Ignore optimistic locking failures when concurrently updating
                                    // random inventory
                                }
                            });
        } catch (RuntimeException e) {
            processedIdempotencyKeys.remove(idempotencyKey);
            throw e;
        }
    }
}
