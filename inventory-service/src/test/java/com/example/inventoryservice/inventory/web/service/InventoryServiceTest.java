/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.exception.ProductAlreadyExistsException;
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private InventoryJOOQRepository inventoryJOOQRepository;

    private InventoryRetryHandler retryHandler;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        retryHandler = new InventoryRetryHandler();
        inventoryService =
                new InventoryService(
                        inventoryRepository,
                        inventoryMapper,
                        inventoryJOOQRepository,
                        retryHandler);
    }

    @Test
    void testSaveInventory() {
        InventoryRequest inventoryRequest = new InventoryRequest("ProductCode1", 100);
        given(inventoryJOOQRepository.findByProductCode("ProductCode1"))
                .willReturn(Optional.empty());
        given(inventoryMapper.toEntity(any(InventoryRequest.class)))
                .willReturn(
                        new Inventory()
                                .setProductCode("ProductCode1")
                                .setAvailableQuantity(100)
                                .setReservedItems(0));
        given(inventoryRepository.save(any(Inventory.class)))
                .willReturn(
                        new Inventory()
                                .setId(1L)
                                .setProductCode("ProductCode1")
                                .setAvailableQuantity(100)
                                .setReservedItems(0));

        Inventory savedInventory = inventoryService.saveInventory(inventoryRequest);

        assertThat(savedInventory).isNotNull();
        assertThat(savedInventory.getId()).isEqualTo(1L);
        assertThat(savedInventory.getProductCode()).isEqualTo("ProductCode1");
    }

    @Test
    void testSaveInventory_Conflict() {
        InventoryRequest inventoryRequest = new InventoryRequest("ProductCode1", 100);
        InventoryRecord activeRecord = new InventoryRecord();
        activeRecord.setProductCode("ProductCode1");
        activeRecord.setDeleted(false);

        given(inventoryJOOQRepository.findByProductCode("ProductCode1"))
                .willReturn(Optional.of(activeRecord));

        assertThatThrownBy(() -> inventoryService.saveInventory(inventoryRequest))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessageContaining("Product with code ProductCode1 already exists");

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }
}
