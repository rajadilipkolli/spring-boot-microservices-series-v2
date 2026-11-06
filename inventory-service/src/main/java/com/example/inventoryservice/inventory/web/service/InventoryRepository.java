/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository strictly reserved for write operations (insert, update, delete). All
 * queries/reads are routed via {@link InventoryJOOQRepository}.
 */
interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Physically deletes inventory with the exact product code, including soft-deleted inventory,
     * within a transaction. Does nothing if absent; persistence failures propagate.
     */
    @Transactional
    void deleteByProductCode(String productCode);
}
