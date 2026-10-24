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

    Optional<Inventory> findByProductCodeAndDeletedFalse(String productCode);

    List<Inventory> findByProductCodeInAndDeletedFalse(List<String> productCodes);

    Optional<Inventory> findByIdAndDeletedFalse(Long id);
}
