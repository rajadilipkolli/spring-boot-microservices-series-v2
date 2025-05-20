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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    /**
     * Helper method to capture inventory state before an operation and verify changes after
     * operation.
     *
     * @param orderDto The order to process
     * @param operation The operation to perform on the order (e.g., confirm, reserve)
     * @param expectedChanges Map of product codes to expected inventory changes
     *     [availableQtyChange, reservedItemsChange]
     */
    private void assertInventoryChanges(
            OrderDto orderDto,
            Function<OrderDto, OrderDto> operation,
            Map<String, int[]> expectedChanges) {

        // Capture initial state of all products
        Map<String, Inventory> initialState =
                inventoryRepository
                        .findByProductCodeIn(new ArrayList<>(expectedChanges.keySet()))
                        .stream()
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        // Perform the operation
        OrderDto result = operation.apply(orderDto);

        // Verify each product's changes
        for (String productCode : expectedChanges.keySet()) {
            Inventory initial = initialState.get(productCode);
            Inventory updated = inventoryRepository.findById(initial.getId()).orElseThrow();

            int[] changes = expectedChanges.get(productCode);
            int availableQtyChange = changes[0];
            int reservedItemsChange = changes[1];

            assertThat(updated.getAvailableQuantity())
                    .isEqualTo(initial.getAvailableQuantity() + availableQtyChange);
            assertThat(updated.getReservedItems())
                    .isEqualTo(initial.getReservedItems() + reservedItemsChange);
        }

        assertThat(result).as("Service should return an OrderDto instance").isNotNull();
    }

    @Test
    void confirmOrderWithConfirmedStatus_ShouldDecreaseReservedItems() { // Arrange
        OrderItemDto item1 = new OrderItemDto(1L, "product1", 20, BigDecimal.TEN);
        OrderItemDto item2 = new OrderItemDto(2L, "product2", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 2L, "CONFIRMED", "TEST", List.of(item1, item2));

        // Define expected changes: [availableQtyChange, reservedItemsChange]
        Map<String, int[]> expectedChanges =
                Map.of(
                        "product1", new int[] {0, -20}, // No change in available, -20 reserved
                        "product2", new int[] {0, -10} // No change in available, -10 reserved
                        );

        // Run operation and assert changes
        assertInventoryChanges(
                orderDto,
                (order) -> {
                    inventoryOrderManageService.confirm(order);
                    return order;
                },
                expectedChanges);
    }

    @Test
    void confirmOrderWithRollbackStatus_ShouldDecreaseReservedItemsAndIncreaseAvailableQuantity() {
        // Arrange
        OrderItemDto item1 = new OrderItemDto(1L, "product1", 20, BigDecimal.TEN);
        OrderItemDto item2 = new OrderItemDto(2L, "product2", 10, BigDecimal.TEN);
        // Not inventory source to trigger rollback logic
        OrderDto orderDto = new OrderDto(1L, 1L, "ROLLBACK", "OTHER_SOURCE", List.of(item1, item2));

        // Define expected changes: [availableQtyChange, reservedItemsChange]
        Map<String, int[]> expectedChanges =
                Map.of(
                        "product1", new int[] {20, -20}, // +20 available, -20 reserved
                        "product2", new int[] {10, -10} // +10 available, -10 reserved
                        );

        // Run operation and assert changes
        assertInventoryChanges(
                orderDto,
                (order) -> {
                    inventoryOrderManageService.confirm(order);
                    return order;
                },
                expectedChanges);
    }

    @Test
    void confirmOrderWithRollbackStatusAndInventorySource_ShouldNotChangeInventory() {
        // Arrange
        OrderItemDto item1 = new OrderItemDto(1L, "product1", 20, BigDecimal.TEN);
        OrderItemDto item2 = new OrderItemDto(2L, "product2", 10, BigDecimal.TEN);
        // inventory source, should not trigger rollback logic
        OrderDto orderDto =
                new OrderDto(1L, 1L, "ROLLBACK", AppConstants.SOURCE, List.of(item1, item2));

        // Define expected changes: [availableQtyChange, reservedItemsChange]
        Map<String, int[]> expectedChanges =
                Map.of(
                        "product1", new int[] {0, 0}, // No change
                        "product2", new int[] {0, 0} // No change
                        );

        // Run operation and assert changes
        assertInventoryChanges(
                orderDto,
                (order) -> {
                    inventoryOrderManageService.confirm(order);
                    return order;
                },
                expectedChanges);
    }

    @Test
    void reserveOrderWithSufficientStock_ShouldReturnAcceptStatus() {
        // Arrange
        OrderItemDto item1 = new OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderItemDto item2 = new OrderItemDto(2L, "product2", 20, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 2L, "NEW", "TEST", List.of(item1, item2));

        // Define expected changes: [availableQtyChange, reservedItemsChange]
        Map<String, int[]> expectedChanges =
                Map.of(
                        "product1", new int[] {-10, 10}, // -10 available, +10 reserved
                        "product2", new int[] {-20, 20} // -20 available, +20 reserved
                        );

        // Run operation and verify changes
        OrderDto resultOrderDto = null;

        // Save the result from the operation
        final OrderDto[] resultHolder = new OrderDto[1];

        assertInventoryChanges(
                orderDto,
                (order) -> {
                    OrderDto result = inventoryOrderManageService.reserve(order);
                    resultHolder[0] = result;
                    return result;
                },
                expectedChanges);

        resultOrderDto = resultHolder[0];

        // Additional assertions on the result
        assertThat(resultOrderDto).isNotNull();
        assertThat(resultOrderDto.status()).isEqualTo("ACCEPT");
        assertThat(resultOrderDto.source()).isEqualTo(AppConstants.SOURCE);
    }

    @Test
    void reserveOrderWithInsufficientStock_ShouldReturnRejectStatus() {
        // Arrange
        OrderItemDto item1 =
                new OrderItemDto(1L, "product1", 120, BigDecimal.TEN); // More than available
        OrderItemDto item2 = new OrderItemDto(2L, "product2", 20, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 2L, "NEW", "TEST", List.of(item1, item2));

        // Define expected changes: [availableQtyChange, reservedItemsChange]
        Map<String, int[]> expectedChanges =
                Map.of(
                        "product1", new int[] {0, 0}, // No change expected
                        "product2", new int[] {0, 0} // No change expected
                        );

        // Run operation and verify changes
        final OrderDto[] resultHolder = new OrderDto[1];

        assertInventoryChanges(
                orderDto,
                (order) -> {
                    OrderDto result = inventoryOrderManageService.reserve(order);
                    resultHolder[0] = result;
                    return result;
                },
                expectedChanges);

        OrderDto resultOrderDto = resultHolder[0];

        // Additional assertions on the result
        assertThat(resultOrderDto).isNotNull();
        assertThat(resultOrderDto.status()).isEqualTo("REJECT");
        assertThat(resultOrderDto.source()).isEqualTo(AppConstants.SOURCE);
    }

    @ParameterizedTest
    @CsvSource({
        // format: orderQty, availableQty, expectedStatus
        // Sufficient stock scenarios
        "10, 100, ACCEPT", // Normal case with plenty of stock
        "100, 100, ACCEPT", // Edge case - exactly available quantity
        "5, 100, ACCEPT", // Small order quantity

        // Insufficient stock scenarios
        "120, 100, REJECT", // Clearly insufficient
        "101, 100, REJECT", // Just over the limit
        "200, 100, REJECT" // Way over the limit
    })
    void parameterizedReserveOrderTest(
            int orderQuantity, int availableQuantity, String expectedStatus) {
        // Setup - set available quantity for product1 to the parameterized value
        product1.setAvailableQuantity(availableQuantity);
        inventoryRepository.save(product1);

        // Arrange the order with the parameterized quantity
        OrderItemDto item1 = new OrderItemDto(1L, "product1", orderQuantity, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(1L, 2L, "NEW", "TEST", List.of(item1));

        // Define expected changes based on expected status
        Map<String, int[]> expectedChanges;
        if ("ACCEPT".equals(expectedStatus)) {
            expectedChanges =
                    Map.of(
                            "product1",
                            new int[] {
                                -orderQuantity, orderQuantity
                            } // If accepted: decrease available, increase reserved
                            );
        } else {
            expectedChanges =
                    Map.of(
                            "product1", new int[] {0, 0} // If rejected: no changes
                            );
        }

        // Run operation and verify changes
        final OrderDto[] resultHolder = new OrderDto[1];

        assertInventoryChanges(
                orderDto,
                (order) -> {
                    OrderDto result = inventoryOrderManageService.reserve(order);
                    resultHolder[0] = result;
                    return result;
                },
                expectedChanges);

        OrderDto resultOrderDto = resultHolder[0];

        // Additional assertions on the result
        assertThat(resultOrderDto).isNotNull();
        assertThat(resultOrderDto.status()).isEqualTo(expectedStatus);
        assertThat(resultOrderDto.source()).isEqualTo(AppConstants.SOURCE);
    }
}
