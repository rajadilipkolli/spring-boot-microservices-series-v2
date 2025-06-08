/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.repositories;

import com.example.inventoryservice.entities.Inventory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findByProductCodeIn(List<String> productCodes);
}
