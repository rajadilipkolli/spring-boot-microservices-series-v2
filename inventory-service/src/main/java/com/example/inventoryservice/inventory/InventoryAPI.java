/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory;

import com.example.inventoryservice.inventory.domain.models.*;
import java.util.List;
import java.util.Optional;

public interface InventoryAPI {

    PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir);

    Optional<InventoryResponse> findInventoryById(Long id);

    Optional<InventoryResponse> findInventoryByProductCode(String productCode);

    PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> codes, int pageNo, int pageSize, String sortBy, String sortDir);

    InventoryResponse saveInventory(InventoryRequest inventoryRequest);

    Optional<InventoryResponse> updateInventoryById(Long id, InventoryRequest inventoryRequest);

    Optional<InventoryResponse> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest);

    void deleteInventoryById(Long id);

    void updateGeneratedInventory(String idempotencyKey, Integer batchSize);

    void reserve(OrderDto orderDto);

    void confirm(OrderDto orderDto);

    void manage(ProductDto productDto);
}
