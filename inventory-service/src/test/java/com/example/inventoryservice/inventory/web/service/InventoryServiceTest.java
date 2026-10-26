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
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.exception.ProductAlreadyExistsException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    void testUpdateGeneratedInventory() {
        // Mock the behavior of dependencies
        given(inventoryRepository.findByProductCodeAndDeletedFalse(anyString()))
                .willReturn(
                        Optional.of(
                                new Inventory()
                                        .setId(1L)
                                        .setProductCode("ProductCode1")
                                        .setAvailableQuantity(100)
                                        .setReservedItems(0)));
        willDoNothing()
                .given(inventoryMapper)
                .updateInventoryFromRequest(any(InventoryRequest.class), any(Inventory.class));
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
        inventoryService.updateGeneratedInventory("test-batch-123", null);

        // Verify interactions
        verify(inventoryRepository, times(101)).findByProductCodeAndDeletedFalse(anyString());
        verify(inventoryRepository, times(101)).save(any(Inventory.class));
    }

    @Test
    void shouldUpdateRequestedGeneratedInventoryBatchSize() {
        given(inventoryRepository.findByProductCodeAndDeletedFalse(anyString()))
                .willReturn(
                        Optional.of(
                                new Inventory()
                                        .setId(1L)
                                        .setProductCode("ProductCode1")
                                        .setAvailableQuantity(100)
                                        .setReservedItems(0)));
        willDoNothing()
                .given(inventoryMapper)
                .updateInventoryFromRequest(any(InventoryRequest.class), any(Inventory.class));

        inventoryService.updateGeneratedInventory("test-batch-456", 5);

        verify(inventoryRepository, times(5)).findByProductCodeAndDeletedFalse(anyString());
        verify(inventoryRepository, times(5)).save(any(Inventory.class));
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

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 10_001})
    void shouldRejectInvalidBatchSizeWithoutConsumingIdempotencyKey(int batchSize) {
        String idempotencyKey = "invalid-batch";

        assertThatThrownBy(
                        () -> inventoryService.updateGeneratedInventory(idempotencyKey, batchSize))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(inventoryRepository, inventoryMapper, inventoryJOOQRepository);

        inventoryService.updateGeneratedInventory(idempotencyKey, 1);

        verify(inventoryRepository)
                .findByProductCodeAndDeletedFalse("ProductCode_" + idempotencyKey + "_0");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10_000})
    void shouldAcceptBatchSizeBoundariesAndIgnoreRepeatedKeys(int batchSize) {
        inventoryService.updateGeneratedInventory("boundary-batch", batchSize);
        inventoryService.updateGeneratedInventory("boundary-batch", batchSize);

        verify(inventoryRepository, times(batchSize)).findByProductCodeAndDeletedFalse(anyString());
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
