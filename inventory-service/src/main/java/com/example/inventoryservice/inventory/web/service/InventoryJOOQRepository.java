/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
interface InventoryJOOQRepository {

    Optional<Inventory> findById(Long customerId);

    Page<Inventory> findAll(Pageable pageable);

    Optional<Inventory> findByProductCode(String productCode);

    Page<Inventory> findByProductCodeIn(List<String> productCodes, Pageable pageable);

    boolean existsByProductCode(String productCode);

    int deleteByProductCode(String productCode);
}
