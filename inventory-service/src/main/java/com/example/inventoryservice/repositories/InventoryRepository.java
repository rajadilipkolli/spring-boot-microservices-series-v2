/* Licensed under Apache-2.0 2021-2022 */
package com.example.inventoryservice.repositories;

import com.example.inventoryservice.entities.Inventory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductCode(String productCode);
}
