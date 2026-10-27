/***
<p>
    Licensed under MIT License Copyright (c) 2025-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.example.common.dto.OrderDto;
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import com.example.inventoryservice.inventory.utils.AppConstants;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.support.TransactionTemplate;

class InventoryOrderManageServiceImplIT extends AbstractIntegrationTest {

    private static final AtomicLong ORDER_ID_GENERATOR = new AtomicLong(1000L);
    private Inventory product1;

    /** Clears persisted inventory and seeds two products with available and reserved quantities. */
    @BeforeEach
    void setUp() {
        // Clear existing processed events and inventory
        processedOrderEventRepository.deleteAllInBatch();
        inventoryRepository.deleteAll();

        // Create test inventory items
        product1 =
                new Inventory()
                        .setProductCode("product1")
                        .setAvailableQuantity(100)
                        .setReservedItems(50);

        Inventory product2 =
                new Inventory()
                        .setProductCode("product2")
                        .setAvailableQuantity(200)
                        .setReservedItems(30);

        // Save the inventory items and update the references with the saved entities
        List<Inventory> savedInventories = inventoryRepository.saveAll(List.of(product1, product2));
        product1 = savedInventories.getFirst();
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
                        .findByProductCodeInAndDeletedFalse(
                                new ArrayList<>(expectedChanges.keySet()))
                        .stream()
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        // Perform the operation
        OrderDto result = operation.apply(orderDto);

        // Verify each product's changes
        for (String productCode : expectedChanges.keySet()) {
            Inventory initial = initialState.get(productCode);
            InventoryRecord updated =
                    inventoryJOOQRepository.findById(initial.getId()).orElseThrow();

            int[] changes = expectedChanges.get(productCode);
            int availableQtyChange = changes[0];
            int reservedItemsChange = changes[1];

            assertThat(updated.getQuantity())
                    .isEqualTo(initial.getAvailableQuantity() + availableQtyChange);
            assertThat(updated.getReservedItems())
                    .isEqualTo(initial.getReservedItems() + reservedItemsChange);
        }

        assertThat(result).as("Service should return an OrderDto instance").isNotNull();
    }

    @Test
    void confirmOrderWithConfirmedStatus_ShouldDecreaseReservedItems() { // Arrange
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 20, BigDecimal.TEN);
        OrderDto.OrderItemDto item2 = new OrderDto.OrderItemDto(2L, "product2", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(orderId, 2L, "CONFIRMED", "TEST", List.of(item1, item2));

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
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 20, BigDecimal.TEN);
        OrderDto.OrderItemDto item2 = new OrderDto.OrderItemDto(2L, "product2", 10, BigDecimal.TEN);
        // Not inventory source to trigger rollback logic
        OrderDto orderDto =
                new OrderDto(orderId, 1L, "ROLLBACK", "OTHER_SOURCE", List.of(item1, item2));

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
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 20, BigDecimal.TEN);
        OrderDto.OrderItemDto item2 = new OrderDto.OrderItemDto(2L, "product2", 10, BigDecimal.TEN);
        // inventory source, should not trigger rollback logic
        OrderDto orderDto =
                new OrderDto(orderId, 1L, "ROLLBACK", AppConstants.SOURCE, List.of(item1, item2));

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
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderDto.OrderItemDto item2 = new OrderDto.OrderItemDto(2L, "product2", 20, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(orderId, 2L, "NEW", "TEST", List.of(item1, item2));

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
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 =
                new OrderDto.OrderItemDto(
                        1L, "product1", 120, BigDecimal.TEN); // More than available
        OrderDto.OrderItemDto item2 = new OrderDto.OrderItemDto(2L, "product2", 20, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(orderId, 2L, "NEW", "TEST", List.of(item1, item2));

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
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 =
                new OrderDto.OrderItemDto(1L, "product1", orderQuantity, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(orderId, 2L, "NEW", "TEST", List.of(item1));

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

    @Test
    void reserveOrderWithDuplicateEvent_ShouldSkipProcessing() {
        stockOrderListener.reset();
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(orderId, 2L, "NEW", "TEST", List.of(item1));

        OrderDto firstResult = inventoryOrderManageService.reserve(orderDto);
        assertThat(firstResult.status()).isEqualTo("ACCEPT");

        // Wait for first event
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(
                                                stockOrderListener.getReceivedOrders().stream()
                                                        .filter(o -> orderId.equals(o.orderId()))
                                                        .toList())
                                        .hasSize(1));

        // Inventory stock should reflect first reservation: available 90, reserved 60
        InventoryRecord afterFirst =
                inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(afterFirst.getQuantity()).isEqualTo(90);
        assertThat(afterFirst.getReservedItems()).isEqualTo(60);

        // Duplicate reserve event should be skipped and return original orderDto
        OrderDto secondResult = inventoryOrderManageService.reserve(orderDto);
        assertThat(secondResult.status()).isEqualTo("NEW");

        // Stock unchanged and publication count remains 1
        InventoryRecord afterSecond =
                inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(afterSecond.getQuantity()).isEqualTo(90);
        assertThat(afterSecond.getReservedItems()).isEqualTo(60);
        assertThat(
                        stockOrderListener.getReceivedOrders().stream()
                                .filter(o -> orderId.equals(o.orderId()))
                                .toList())
                .hasSize(1);
    }

    @Test
    void confirmOrderWithDuplicateExternalRollback_ShouldRestoreStockOnlyOnce() {
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderDto rollbackDto = new OrderDto(orderId, 2L, "ROLLBACK", "PAYMENT", List.of(item1));

        // Initial state from setUp: available 100, reserved 50
        inventoryOrderManageService.confirm(rollbackDto);
        InventoryRecord afterFirst =
                inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(afterFirst.getQuantity()).isEqualTo(110);
        assertThat(afterFirst.getReservedItems()).isEqualTo(40);

        // Duplicate external rollback should be skipped
        inventoryOrderManageService.confirm(rollbackDto);
        InventoryRecord afterSecond =
                inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(afterSecond.getQuantity()).isEqualTo(110);
        assertThat(afterSecond.getReservedItems()).isEqualTo(40);
    }

    @Test
    void rejectedNewOrderRedelivery_KeepsStockUnchangedAndStoresReject() {
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        // Request 150 of product1 (available is 100) -> should be rejected
        OrderDto.OrderItemDto item1 =
                new OrderDto.OrderItemDto(1L, "product1", 150, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(orderId, 2L, "NEW", "ORDER", List.of(item1));

        OrderDto firstResult = inventoryOrderManageService.reserve(orderDto);
        assertThat(firstResult.status()).isEqualTo("REJECT");

        // Verify result_status in DB is REJECT
        assertThat(
                        processedOrderEventRepository.findResultStatusByOrderIdAndReserveEventType(
                                orderId))
                .contains("REJECT");

        // Redelivery with blank attempt should skip and keep stock unchanged
        OrderDto secondResult = inventoryOrderManageService.reserve(orderDto);
        assertThat(secondResult.status()).isEqualTo("NEW");

        InventoryRecord afterRedelivery =
                inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(afterRedelivery.getQuantity()).isEqualTo(100);
        assertThat(afterRedelivery.getReservedItems()).isEqualTo(50);
    }

    @Test
    void reserveOrder_AttemptsAAndB_ReservesStockOnceAndPublishesOneReplyPerAttempt() {
        stockOrderListener.reset();
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderDto orderDtoA = new OrderDto(orderId, 2L, "NEW", "ORDER", List.of(item1), "attempt-A");
        OrderDto orderDtoB = new OrderDto(orderId, 2L, "NEW", "ORDER", List.of(item1), "attempt-B");

        // First attempt A
        OrderDto resultA = inventoryOrderManageService.reserve(orderDtoA);
        assertThat(resultA.status()).isEqualTo("ACCEPT");
        assertThat(resultA.attemptId()).isEqualTo("attempt-A");

        // Second attempt B
        OrderDto resultB = inventoryOrderManageService.reserve(orderDtoB);
        assertThat(resultB.status()).isEqualTo("ACCEPT");
        assertThat(resultB.attemptId()).isEqualTo("attempt-B");
        assertThat(resultB.source()).isEqualTo(AppConstants.SOURCE);

        // Verify stock reserved only once
        InventoryRecord stock = inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(90);
        assertThat(stock.getReservedItems()).isEqualTo(60);

        // Verify events published: one for attempt A, one for attempt B
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            List<OrderDto> orders =
                                    stockOrderListener.getReceivedOrders().stream()
                                            .filter(o -> orderId.equals(o.orderId()))
                                            .toList();
                            assertThat(orders).hasSize(2);
                            assertThat(orders)
                                    .extracting(OrderDto::attemptId)
                                    .containsExactlyInAnyOrder("attempt-A", "attempt-B");
                            assertThat(orders)
                                    .filteredOn(order -> "attempt-B".equals(order.attemptId()))
                                    .singleElement()
                                    .extracting(OrderDto::status)
                                    .isEqualTo("ACCEPT");
                        });
    }

    @Test
    void concurrentTwoThreadDuplicateNewOrder_OnlyOneReservationAndOneReserveMarker()
            throws Exception {
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(orderId, 2L, "NEW", "ORDER", List.of(item1));

        CountDownLatch startLatch = new CountDownLatch(1);
        CompletableFuture<OrderDto> future1 =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                startLatch.await();
                            } catch (InterruptedException ignored) {
                            }
                            return inventoryOrderManageService.reserve(orderDto);
                        });
        CompletableFuture<OrderDto> future2 =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                startLatch.await();
                            } catch (InterruptedException ignored) {
                            }
                            return inventoryOrderManageService.reserve(orderDto);
                        });

        startLatch.countDown();
        OrderDto r1 = future1.get();
        OrderDto r2 = future2.get();

        // Exactly one should be ACCEPT, the other should be NEW (duplicate skipped)
        List<String> statuses = List.of(r1.status(), r2.status());
        assertThat(statuses).containsExactlyInAnyOrder("ACCEPT", "NEW");

        // Stock reserved only once
        InventoryRecord stock = inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(90);
        assertThat(stock.getReservedItems()).isEqualTo(60);

        // Exactly one RESERVE marker
        assertThat(
                        processedOrderEventRepository.findResultStatusByOrderIdAndReserveEventType(
                                orderId))
                .contains("ACCEPT");
    }

    @Test
    void failureAfterClaim_RollsBackMarkerAndAllowsSuccessfulLaterDelivery() {
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderDto orderDto =
                new OrderDto(orderId, 2L, "NEW", "ORDER", List.of(item1), "attempt-fail");

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // Simulate a transaction failure after claim (e.g. database error or rollback)
        assertThatThrownBy(
                        () ->
                                transactionTemplate.execute(
                                        status -> {
                                            inventoryOrderManageService.reserve(orderDto);
                                            throw new RuntimeException("Simulated failure");
                                        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated failure");

        // The RESERVE marker should have been rolled back
        assertThat(
                        processedOrderEventRepository.findResultStatusByOrderIdAndReserveEventType(
                                orderId))
                .isEmpty();

        // Subsequent delivery succeeds and reserves stock
        OrderDto secondResult = inventoryOrderManageService.reserve(orderDto);
        assertThat(secondResult.status()).isEqualTo("ACCEPT");

        InventoryRecord stock = inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(90);
        assertThat(stock.getReservedItems()).isEqualTo(60);
    }

    @Test
    void confirmOrderWithDuplicateEvent_ShouldSkipProcessing() {
        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item1 = new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(orderId, 2L, "CONFIRMED", "TEST", List.of(item1));

        inventoryOrderManageService.confirm(orderDto);
        InventoryRecord afterFirst =
                inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(afterFirst.getReservedItems()).isEqualTo(40);

        // Duplicate confirm should be skipped and not deduct reserved items again
        inventoryOrderManageService.confirm(orderDto);
        InventoryRecord afterSecond =
                inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(afterSecond.getReservedItems()).isEqualTo(40);
    }

    @Test
    void reserveOrderForSoftDeletedProduct_ShouldRejectReservation() {
        // Soft delete product1
        product1.markDeleted();
        inventoryRepository.save(product1);

        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(orderId, 2L, "NEW", "TEST", List.of(item));

        OrderDto result = inventoryOrderManageService.reserve(orderDto);
        assertThat(result.status()).isEqualTo("REJECT");
    }

    @Test
    void confirmAndRollbackForSoftDeletedProduct_ShouldComplete() {
        // Soft delete product1
        product1.markDeleted();
        inventoryRepository.save(product1);

        Long orderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "product1", 10, BigDecimal.TEN);
        OrderDto confirmDto = new OrderDto(orderId, 2L, "CONFIRMED", "TEST", List.of(item));

        // Unfiltered confirm should successfully deduct reserved items on soft-deleted row
        inventoryOrderManageService.confirm(confirmDto);
        InventoryRecord afterConfirm =
                inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(afterConfirm.getReservedItems()).isEqualTo(40);

        Long rollbackOrderId = ORDER_ID_GENERATOR.incrementAndGet();
        OrderDto rollbackDto = new OrderDto(rollbackOrderId, 2L, "ROLLBACK", "TEST", List.of(item));
        // Unfiltered rollback should successfully release reserved items
        inventoryOrderManageService.confirm(rollbackDto);
        InventoryRecord afterRollback =
                inventoryJOOQRepository.findById(product1.getId()).orElseThrow();
        assertThat(afterRollback.getQuantity()).isEqualTo(110);
        assertThat(afterRollback.getReservedItems()).isEqualTo(30);
    }
}
