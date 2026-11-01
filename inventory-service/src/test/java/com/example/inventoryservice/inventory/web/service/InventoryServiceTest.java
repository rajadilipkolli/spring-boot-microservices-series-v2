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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private InventoryJOOQRepository inventoryJOOQRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService =
                new InventoryService(
                        inventoryRepository, inventoryMapper, inventoryJOOQRepository, null);
        ReflectionTestUtils.setField(inventoryService, "self", inventoryService);
    }

    @Test
    void testSaveInventory() {
        InventoryRequest inventoryRequest = new InventoryRequest("ProductCode1", 100);
        given(inventoryRepository.findByProductCode("ProductCode1")).willReturn(Optional.empty());
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
        given(inventoryRepository.findByProductCode("ProductCode1"))
                .willReturn(Optional.of(new Inventory().setProductCode("ProductCode1")));

        assertThatThrownBy(() -> inventoryService.saveInventory(inventoryRequest))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessageContaining("Product with code ProductCode1 already exists");

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }
}
