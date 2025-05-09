/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.inventoryservice.common.AbstractIntegrationTest;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.utils.AppConstants;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryOrderManageServiceIT extends AbstractIntegrationTest {

    private Inventory product1;
    private Inventory product2;

    @BeforeEach
    void setUp() {
        // Clear existing inventory
        inventoryRepository.deleteAll();

        // Create test inventory items
        product1 =
                new Inventory()
                        .setProductCode("product1")
                        .setAvailableQuantity(100)
                        .setReservedItems(50);

        product2 =
                new Inventory()
                        .setProductCode("product2")
                        .setAvailableQuantity(200)
                        .setReservedItems(30);

        // Save the inventory items and update the references with the saved entities
        List<Inventory> savedInventories = inventoryRepository.saveAll(List.of(product1, product2));
        product1 = savedInventories.get(0);
        product2 = savedInventories.get(1);
    }

    @Test
    void confirmOrderWithConfirmedStatus_ShouldDecreaseReservedItems() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus("CONFIRMED");

        OrderItemDto item1 = new OrderItemDto(1L, "product1", 20, BigDecimal.TEN);
        OrderItemDto item2 = new OrderItemDto(2L, "product2", 10, BigDecimal.TEN);
        orderDto.setItems(List.of(item1, item2));

        // Initial state verification
        Inventory initialProduct1 = inventoryRepository.findById(product1.getId()).orElseThrow();
        Inventory initialProduct2 = inventoryRepository.findById(product2.getId()).orElseThrow();

        assertThat(initialProduct1.getReservedItems()).isEqualTo(50);
        assertThat(initialProduct2.getReservedItems()).isEqualTo(30);

        // Act
        inventoryOrderManageService.confirm(orderDto);

        // Assert
        Inventory updatedProduct1 = inventoryRepository.findById(product1.getId()).orElseThrow();
        Inventory updatedProduct2 = inventoryRepository.findById(product2.getId()).orElseThrow();

        // Reserved items should decrease but available quantity should remain unchanged
        assertThat(updatedProduct1.getReservedItems()).isEqualTo(30); // 50 - 20
        assertThat(updatedProduct1.getAvailableQuantity()).isEqualTo(100); // unchanged

        assertThat(updatedProduct2.getReservedItems()).isEqualTo(20); // 30 - 10
        assertThat(updatedProduct2.getAvailableQuantity()).isEqualTo(200); // unchanged
    }

    @Test
    void confirmOrderWithRollbackStatus_ShouldDecreaseReservedItemsAndIncreaseAvailableQuantity() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus(AppConstants.ROLLBACK);
        orderDto.setSource("OTHER_SOURCE"); // Not inventory source to trigger rollback logic

        OrderItemDto item1 = new OrderItemDto(1L, "product1", 20, BigDecimal.TEN);
        OrderItemDto item2 = new OrderItemDto(2L, "product2", 10, BigDecimal.TEN);
        orderDto.setItems(List.of(item1, item2));

        // Initial state verification
        Inventory initialProduct1 = inventoryRepository.findById(product1.getId()).orElseThrow();
        Inventory initialProduct2 = inventoryRepository.findById(product2.getId()).orElseThrow();

        assertThat(initialProduct1.getReservedItems()).isEqualTo(50);
        assertThat(initialProduct1.getAvailableQuantity()).isEqualTo(100);
        assertThat(initialProduct2.getReservedItems()).isEqualTo(30);
        assertThat(initialProduct2.getAvailableQuantity()).isEqualTo(200);

        // Act
        inventoryOrderManageService.confirm(orderDto);

        // Assert
        Inventory updatedProduct1 = inventoryRepository.findById(product1.getId()).orElseThrow();
        Inventory updatedProduct2 = inventoryRepository.findById(product2.getId()).orElseThrow();

        // Reserved items should decrease and available quantity should increase
        assertThat(updatedProduct1.getReservedItems()).isEqualTo(30); // 50 - 20
        assertThat(updatedProduct1.getAvailableQuantity()).isEqualTo(120); // 100 + 20

        assertThat(updatedProduct2.getReservedItems()).isEqualTo(20); // 30 - 10
        assertThat(updatedProduct2.getAvailableQuantity()).isEqualTo(210); // 200 + 10
    }

    @Test
    void confirmOrderWithRollbackStatusAndInventorySource_ShouldNotChangeInventory() {
        // Arrange
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setStatus(AppConstants.ROLLBACK);
        orderDto.setSource(
                AppConstants.SOURCE); // inventory source, should not trigger rollback logic

        OrderItemDto item1 = new OrderItemDto(1L, "product1", 20, BigDecimal.TEN);
        OrderItemDto item2 = new OrderItemDto(2L, "product2", 10, BigDecimal.TEN);
        orderDto.setItems(List.of(item1, item2));

        // Initial state verification
        Inventory initialProduct1 = inventoryRepository.findById(product1.getId()).orElseThrow();
        Inventory initialProduct2 = inventoryRepository.findById(product2.getId()).orElseThrow();

        // Act
        inventoryOrderManageService.confirm(orderDto);

        // Assert
        Inventory updatedProduct1 = inventoryRepository.findById(product1.getId()).orElseThrow();
        Inventory updatedProduct2 = inventoryRepository.findById(product2.getId()).orElseThrow();

        // Reserved items and available quantity should remain unchanged for ROLLBACK with INVENTORY
        // source
        assertThat(updatedProduct1.getReservedItems())
                .isEqualTo(initialProduct1.getReservedItems());
        assertThat(updatedProduct1.getAvailableQuantity())
                .isEqualTo(initialProduct1.getAvailableQuantity());

        assertThat(updatedProduct2.getReservedItems())
                .isEqualTo(initialProduct2.getReservedItems());
        assertThat(updatedProduct2.getAvailableQuantity())
                .isEqualTo(initialProduct2.getAvailableQuantity());
    }
}
