/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.repositories;

import com.example.inventoryservice.entities.Inventory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryJOOQRepository {

    Optional<Inventory> findById(Long customerId);

    Page<Inventory> findAll(Pageable pageable);

    Optional<Inventory> findByProductCode(String productCode);

    List<Inventory> findByProductCodeIn(List<String> productCodes);

    List<Inventory> findByProductCodeInAndQuantityAvailable(List<String> productCodes);
}
