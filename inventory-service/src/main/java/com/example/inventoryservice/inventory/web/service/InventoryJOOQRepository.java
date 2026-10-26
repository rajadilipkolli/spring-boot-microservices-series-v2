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

    /**
     * Returns inventory by ID, including soft-deleted records, or empty if absent. Database
     * failures propagate.
     */
    Optional<InventoryRecord> findById(Long inventoryId);

    /**
     * Returns the requested sorted page and total count of active inventory. Database failures
     * propagate.
     *
     * @throws org.springframework.dao.InvalidDataAccessApiUsageException if a sort property has no
     *     accessible table field
     */
    Page<InventoryRecord> findAllAndDeletedFalse(Pageable pageable);

    /**
     * Returns active inventory by exact product code, or empty if absent or deleted. Database
     * failures propagate.
     */
    Optional<InventoryRecord> findByProductCodeAndDeletedFalse(String productCode);

    /**
     * Returns the requested sorted page and total count of active inventory matching the supplied
     * codes. Missing or deleted products are omitted; database failures propagate.
     *
     * @throws org.springframework.dao.InvalidDataAccessApiUsageException if a sort property has no
     *     accessible table field
     */
    Page<InventoryRecord> findByProductCodeInAndDeletedFalse(
            List<String> productCodes, Pageable pageable);

    /**
     * Returns whether active inventory exists for the exact product code. Database failures
     * propagate.
     */
    boolean existsByProductCodeAndDeletedFalse(String productCode);

    /**
     * Returns active inventory by ID, or empty if absent or deleted. Database failures propagate.
     */
    Optional<InventoryRecord> findByIdAndDeletedFalse(Long inventoryId);

    /** Returns whether active inventory exists for the ID. Database failures propagate. */
    boolean existsByIdAndDeletedFalse(Long inventoryId);
}
