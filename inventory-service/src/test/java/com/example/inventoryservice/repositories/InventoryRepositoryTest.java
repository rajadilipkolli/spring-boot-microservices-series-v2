/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventoryservice.entities.Inventory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@Disabled
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.test.database.replace=none",
            "spring.datasource.url=jdbc:tc:postgresql:16.0-alpine:///databasename"
        })
class InventoryRepositoryTest {

    @Autowired private InventoryRepository inventoryRepository;

    @Autowired private InventoryJOOQRepository inventoryJOOQRepository;

    @BeforeEach
    void setUpData() {
        inventoryRepository.deleteAll();
    }

    @Test
    void findByProductCodeInAndQuantityAvailable() {
        List<Inventory> inventoryList =
                List.of(
                        new Inventory(null, "product1", 10, 0),
                        new Inventory(null, "product2", 0, 0));
        this.inventoryRepository.saveAll(inventoryList);

        List<Inventory> findAvailableInventory =
                this.inventoryJOOQRepository.findByProductCodeInAndQuantityAvailable(
                        List.of("product1", "product2"));

        assertThat(findAvailableInventory).isNotEmpty().hasSize(1);
    }
}
