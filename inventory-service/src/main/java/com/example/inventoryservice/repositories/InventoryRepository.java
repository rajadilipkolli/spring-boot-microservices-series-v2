/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.repositories;

import com.example.inventoryservice.entities.Inventory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductCode(String productCode);

    List<Inventory> findByProductCodeIn(List<String> productCodes);

    @Query(
            "select i from Inventory i where i.productCode in :productCodes and i.availableQuantity > 0")
    List<Inventory> findByProductCodeInAndQuantityAvailable(
            @Param("productCodes") List<String> productCodes);
}
