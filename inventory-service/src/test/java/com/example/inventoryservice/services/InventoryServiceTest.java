/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.mapper.InventoryMapper;
import com.example.inventoryservice.repositories.InventoryJOOQRepository;
import com.example.inventoryservice.repositories.InventoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private InventoryJOOQRepository inventoryJOOQRepository;

    @InjectMocks private InventoryService inventoryService;

    @Test
    void testUpdateGeneratedInventory() {
        // Mock the behavior of dependencies
        when(inventoryJOOQRepository.findById(anyLong())).thenReturn(Optional.of(new Inventory()));
        doAnswer(
                        invocation -> {
                            Inventory inventory = invocation.getArgument(0);
                            assertThat(inventory.getProductCode()).contains("ProductCode");
                            assertThat(
                                            inventory.getAvailableQuantity() >= 1
                                                    && inventory.getAvailableQuantity() <= 10000)
                                    .isTrue();
                            return null;
                        })
                .when(inventoryRepository)
                .save(any(Inventory.class));

        // Execute the method to test
        inventoryService.updateGeneratedInventory();

        // Verify interactions
        verify(inventoryJOOQRepository, times(101)).findById(anyLong());
        verify(inventoryRepository, times(101)).save(any(Inventory.class));
    }
}
