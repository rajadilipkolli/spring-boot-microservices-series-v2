/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory;

import com.example.common.dto.PagedResult;
import com.example.inventoryservice.inventory.domain.models.*;
import java.util.List;
import java.util.Optional;

public interface InventoryAPI {

    /**
     * Returns a sorted page of inventory records.
     *
     * @param pageNo zero-based page index
     * @param pageSize maximum number of records per page
     * @param sortBy inventory property to sort by
     * @param sortDir {@code asc} for ascending order; other values select descending order
     * @return inventory records and pagination metadata
     */
    PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir);

    /**
     * Finds inventory by its identifier.
     *
     * @param id inventory identifier
     * @return the matching inventory, or an empty optional when absent
     */
    Optional<InventoryResponse> findInventoryById(Long id);

    /**
     * Finds inventory by its product code.
     *
     * @param productCode product code to look up
     * @return the matching inventory, or an empty optional when absent
     */
    Optional<InventoryResponse> findInventoryByProductCode(String productCode);

    /**
     * Returns a sorted page restricted to the supplied product codes.
     *
     * @param codes product codes to include
     * @param pageNo zero-based page index
     * @param pageSize maximum number of records per page
     * @param sortBy inventory property to sort by
     * @param sortDir {@code asc} for ascending order; other values select descending order
     * @return matching inventory records and pagination metadata
     */
    PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> codes, int pageNo, int pageSize, String sortBy, String sortDir);

    /**
     * Creates inventory for a product code that is not already present.
     *
     * @param inventoryRequest product code and initial available quantity
     * @return the saved inventory
     * @throws com.example.inventoryservice.inventory.exception.ProductAlreadyExistsException if the
     *     product code already exists
     */
    InventoryResponse saveInventory(InventoryRequest inventoryRequest);

    /**
     * Updates the product code and available quantity of an existing inventory record.
     *
     * @param id inventory identifier
     * @param inventoryRequest replacement product code and available quantity
     * @return the updated inventory, or an empty optional when the identifier is absent
     */
    Optional<InventoryResponse> updateInventoryById(Long id, InventoryRequest inventoryRequest);

    /**
     * Updates an inventory record located by product code.
     *
     * @param productCode product code to look up
     * @param inventoryRequest replacement product code and available quantity
     * @return the updated inventory, or an empty optional when the product code is absent
     */
    Optional<InventoryResponse> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest);

    /**
     * Deletes inventory by its identifier.
     *
     * @param id identifier of the inventory to delete
     */
    void deleteInventoryById(Long id);

    /**
     * Randomizes available quantities for existing products in a generated batch.
     *
     * <p>Product codes follow {@code ProductCode_<key>_<index>}. Missing products are skipped, and
     * repeated keys are ignored for the lifetime of the service instance.
     *
     * @param idempotencyKey key identifying the batch and preventing repeated processing
     * @param batchSize number of product codes to visit, or {@code null} to use 101
     * @throws IllegalArgumentException if a supplied batch size is outside 1 through 10,000
     */
    void updateGeneratedInventory(String idempotencyKey, Integer batchSize);

    /**
     * Reserves stock for a {@code NEW} order and publishes its acceptance or rejection.
     *
     * <p>Orders with other statuses are ignored. Rejected orders leave stock unchanged.
     *
     * @param orderDto order and item quantities to reserve
     */
    void reserve(OrderDto orderDto);

    /**
     * Consumes reserved stock for confirmed orders or restores stock for external rollbacks.
     *
     * <p>Missing products are skipped. Rollbacks originating from {@code INVENTORY} and statuses
     * other than {@code CONFIRMED} or {@code ROLLBACK} leave quantities unchanged.
     *
     * @param orderDto order whose reservation should be finalized or rolled back
     */
    void confirm(OrderDto orderDto);

    /**
     * Creates inventory for a product, ignoring an already present product code.
     *
     * @param productDto product whose code identifies the inventory record
     */
    void manage(ProductDto productDto);
}
