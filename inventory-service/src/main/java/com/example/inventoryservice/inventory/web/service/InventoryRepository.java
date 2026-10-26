/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductCode(String productCode);

    List<Inventory> findByProductCodeIn(List<String> productCodes);

    /**
     * Finds active inventory by exact product code, or returns empty if absent or deleted.
     * Persistence failures propagate.
     */
    Optional<Inventory> findByProductCodeAndDeletedFalse(String productCode);

    /**
     * Returns active inventory matching the supplied codes for new reservations. Missing or deleted
     * products are omitted; persistence failures propagate.
     */
    List<Inventory> findByProductCodeInAndDeletedFalse(List<String> productCodes);

    /**
     * Finds active inventory by ID, or returns empty if absent or deleted. Persistence failures
     * propagate.
     */
    Optional<Inventory> findByIdAndDeletedFalse(Long id);

    void deleteByProductCode(String productCode);
}
