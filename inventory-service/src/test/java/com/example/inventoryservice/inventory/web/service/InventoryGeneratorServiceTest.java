/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryGeneratorServiceTest {

    @Mock private InventoryService inventoryService;

    private InventoryGeneratorService inventoryGeneratorService;

    @BeforeEach
    void setUp() {
        inventoryGeneratorService = new InventoryGeneratorService(inventoryService);
    }

    @Test
    void testUpdateGeneratedInventory() {
        Inventory mockInventory =
                new Inventory()
                        .setId(1L)
                        .setProductCode("ProductCode1")
                        .setAvailableQuantity(100)
                        .setReservedItems(0);
        given(inventoryService.findInventoryByProductCode(anyString()))
                .willReturn(Optional.of(mockInventory));
        given(inventoryService.updateInventory(any(Inventory.class), any(InventoryRequest.class)))
                .willAnswer(
                        invocation -> {
                            InventoryRequest request = invocation.getArgument(1);
                            assertThat(request.productCode()).contains("ProductCode");
                            assertThat(
                                            request.availableQuantity() >= 1
                                                    && request.availableQuantity() <= 10000)
                                    .isTrue();
                            return mockInventory;
                        });

        inventoryGeneratorService.updateGeneratedInventory("test-batch-123", null);

        verify(inventoryService, times(101)).findInventoryByProductCode(anyString());
        verify(inventoryService, times(101))
                .updateInventory(any(Inventory.class), any(InventoryRequest.class));
    }

    @Test
    void shouldUpdateRequestedGeneratedInventoryBatchSize() {
        Inventory mockInventory =
                new Inventory()
                        .setId(1L)
                        .setProductCode("ProductCode1")
                        .setAvailableQuantity(100)
                        .setReservedItems(0);
        given(inventoryService.findInventoryByProductCode(anyString()))
                .willReturn(Optional.of(mockInventory));
        given(inventoryService.updateInventory(any(Inventory.class), any(InventoryRequest.class)))
                .willReturn(mockInventory);

        inventoryGeneratorService.updateGeneratedInventory("test-batch-456", 5);

        verify(inventoryService, times(5)).findInventoryByProductCode(anyString());
        verify(inventoryService, times(5))
                .updateInventory(any(Inventory.class), any(InventoryRequest.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 10_001})
    void shouldRejectInvalidBatchSizeWithoutConsumingIdempotencyKey(int batchSize) {
        String idempotencyKey = "invalid-batch";

        assertThatThrownBy(
                        () ->
                                inventoryGeneratorService.updateGeneratedInventory(
                                        idempotencyKey, batchSize))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(inventoryService);

        inventoryGeneratorService.updateGeneratedInventory(idempotencyKey, 1);

        verify(inventoryService).findInventoryByProductCode("ProductCode_" + idempotencyKey + "_0");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10_000})
    void shouldAcceptBatchSizeBoundariesAndIgnoreRepeatedKeys(int batchSize) {
        inventoryGeneratorService.updateGeneratedInventory("boundary-batch", batchSize);
        inventoryGeneratorService.updateGeneratedInventory("boundary-batch", batchSize);

        verify(inventoryService, times(batchSize)).findInventoryByProductCode(anyString());
    }

    @Test
    void shouldRemoveIdempotencyKeyOnNonOptimisticRuntimeExceptionToAllowRetry() {
        String idempotencyKey = "retryable-batch";
        given(inventoryService.findInventoryByProductCode(anyString()))
                .willThrow(new RuntimeException("Database connection failure"))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () -> inventoryGeneratorService.updateGeneratedInventory(idempotencyKey, 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection failure");

        // Retry should be allowed and processed
        inventoryGeneratorService.updateGeneratedInventory(idempotencyKey, 1);

        verify(inventoryService, times(2))
                .findInventoryByProductCode("ProductCode_" + idempotencyKey + "_0");
    }
}
