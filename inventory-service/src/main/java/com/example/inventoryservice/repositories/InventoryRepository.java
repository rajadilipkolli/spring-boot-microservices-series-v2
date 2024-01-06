/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.repositories;

import com.example.inventoryservice.entities.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @Modifying
    @Transactional
    void deleteByProductCode(String productCode);
}
