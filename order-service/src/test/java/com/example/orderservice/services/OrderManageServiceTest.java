/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.orderservice.model.request.OrderRequest;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderGeneratorServiceTest {

    @Mock private OrderService orderService;
    @InjectMocks private OrderGeneratorService orderGeneratorService;

    @Captor private ArgumentCaptor<List<OrderRequest>> orderRequestsCaptor;

    @Test
    void shouldGenerateOrdersInBatches() {
        // Arrange
        int expectedOrderCount = 10_000;
        int expectedBatchCount = expectedOrderCount / 100; // BATCH_SIZE = 100

        // Act
        orderGeneratorService.generateOrders("dummy-key", null);

        // Assert
        // Capture all batch calls and verify their structure
        verify(orderService, times(expectedBatchCount))
                .saveBatchOrders(orderRequestsCaptor.capture());
        List<List<OrderRequest>> allBatches = orderRequestsCaptor.getAllValues();

        // Verify batch size
        assertThat(allBatches).hasSize(expectedBatchCount);

        // Verify all batches adhere to expected size
        assertThat(allBatches)
                .allSatisfy(
                        batch ->
                                assertThat(batch)
                                        .hasSize(100)); // Each batch should have 100 orders

        // Verify no further interactions with orderService
        verifyNoMoreInteractions(orderService);
    }

    @Test
    void shouldGenerateValidOrderRequests() {
        // Act
        orderGeneratorService.generateOrders("dummy-key", null);

        // Assert
        verify(orderService, atLeastOnce()).saveBatchOrders(orderRequestsCaptor.capture());
        List<List<OrderRequest>> allBatches = orderRequestsCaptor.getAllValues();

        for (List<OrderRequest> batch : allBatches) {
            assertThat(batch)
                    .isNotEmpty()
                    .allSatisfy(
                            order -> {
                                // Customer ID assertions
                                assertThat(order.customerId()).isPositive();
                                assertThat(order.customerId())
                                        .isBetween(1L, 100L); // Based on implementation

                                // Items assertions
                                assertThat(order.items())
                                        .isNotEmpty()
                                        .hasSize(2)
                                        .allSatisfy(
                                                item -> {
                                                    assertThat(item.productCode())
                                                            .isNotNull()
                                                            .isNotBlank()
                                                            .startsWith("ProductCode");
                                                    assertThat(item.quantity())
                                                            .isPositive()
                                                            .isBetween(1, 6); // RAND.nextInt(5) + 1
                                                    assertThat(item.productPrice())
                                                            .isNotNull()
                                                            .isPositive();
                                                });

                                // Delivery address assertions
                                assertThat(order.deliveryAddress()).isNotNull();
                                assertThat(order.deliveryAddress().addressLine1()).isNotBlank();
                                assertThat(order.deliveryAddress().city()).isNotBlank();
                                assertThat(order.deliveryAddress().state()).isNotBlank();
                                assertThat(order.deliveryAddress().zipCode()).isNotBlank();
                                assertThat(order.deliveryAddress().country()).isNotBlank();
                            });
        }
    }

    @Test
    void shouldHandleParallelOrderGeneration() throws InterruptedException {
        // Arrange
        int numThreads = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        // Act
        try {
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executorService.submit(
                        () -> orderGeneratorService.generateOrders("dummy-key-" + threadId, null));
            }
        } finally {
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(terminated)
                    .isTrue()
                    .as("Executor service should terminate within the timeout");
        }

        // Assert
        // Each thread will generate 10,000 orders in batches of 100
        int expectedBatchesPerThread = 10_000 / 100;
        int totalExpectedBatches = expectedBatchesPerThread * numThreads;

        // Capture all batch calls to verify their structure
        verify(orderService, times(totalExpectedBatches))
                .saveBatchOrders(orderRequestsCaptor.capture());
        List<List<OrderRequest>> allBatches = orderRequestsCaptor.getAllValues();

        // Verify we got the expected number of batches
        assertThat(allBatches).hasSize(totalExpectedBatches);

        // Verify each batch has the correct size
        assertThat(allBatches)
                .allSatisfy(
                        batch ->
                                assertThat(batch)
                                        .hasSize(100)
                                        .as("Each batch should contain exactly 100 orders"));

        // Verify the total number of orders
        int totalOrders = allBatches.stream().mapToInt(List::size).sum();
        assertThat(totalOrders)
                .isEqualTo(numThreads * 10_000)
                .as("Total orders should match expectations");
    }

    @Test
    void shouldGenerateRequestedOrderBatchSize() {
        orderGeneratorService.generateOrders("batch-size-key", 250);

        verify(orderService, times(3)).saveBatchOrders(orderRequestsCaptor.capture());
        List<List<OrderRequest>> allBatches = orderRequestsCaptor.getAllValues();

        assertThat(allBatches).extracting(List::size).containsExactlyInAnyOrder(100, 100, 50);
        assertThat(allBatches.stream().mapToInt(List::size).sum()).isEqualTo(250);
    }

    @Test
    void shouldBeIdempotentOnSameKey() {
        // Arrange
        String idempotencyKey = "idempotent-key-123";

        // Act
        // Call it multiple times with the same key
        orderGeneratorService.generateOrders(idempotencyKey, null);
        orderGeneratorService.generateOrders(idempotencyKey, null);
        orderGeneratorService.generateOrders(idempotencyKey, null);

        // Assert
        // Only one generation (10_000 orders / 100 per batch = 100 batches) should have been
        // triggered
        int expectedBatches = 10_000 / 100;
        verify(orderService, times(expectedBatches)).saveBatchOrders(anyList());
    }
}
