/* Licensed under Apache-2.0 2023 */
package com.example.inventoryservice.repositories;

import static com.example.inventoryservice.utils.AppConstants.PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventoryservice.entities.Inventory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class InventoryRepositoryTest {

    @Autowired private InventoryRepository inventoryRepository;

    @Test
    void findByProductCodeInAndQuantityAvailable() {
        List<Inventory> inventoryList =
                List.of(new Inventory(1L, "product1", 10, 0), new Inventory(2L, "product2", 0, 0));
        this.inventoryRepository.saveAll(inventoryList);

        List<Inventory> findAvailableInventory =
                this.inventoryRepository.findByProductCodeInAndQuantityAvailable(
                        List.of("product1", "product2"));

        assertThat(findAvailableInventory).isNotEmpty().hasSize(1);
    }
}
