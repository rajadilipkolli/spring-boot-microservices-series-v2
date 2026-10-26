/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
interface InventoryJOOQRepository {

    Optional<InventoryRecord> findById(Long inventoryId);

    Page<InventoryRecord> findAllAndDeletedFalse(Pageable pageable);

    Optional<InventoryRecord> findByProductCodeAndDeletedFalse(String productCode);

    Page<InventoryRecord> findByProductCodeInAndDeletedFalse(
            List<String> productCodes, Pageable pageable);

    boolean existsByProductCodeAndDeletedFalse(String productCode);

    Optional<InventoryRecord> findByIdAndDeletedFalse(Long inventoryId);

    boolean existsByIdAndDeletedFalse(Long inventoryId);
}
