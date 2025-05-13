/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
        orderGeneratorService.generateOrders();

        // Assert
        verify(orderService, times(expectedBatchCount)).saveBatchOrders(anyList());
    }

    @Test
    void shouldGenerateValidOrderRequests() {
        // Act
        orderGeneratorService.generateOrders();

        // Assert
        verify(orderService, atLeastOnce()).saveBatchOrders(orderRequestsCaptor.capture());
        List<List<OrderRequest>> allBatches = orderRequestsCaptor.getAllValues();

        for (List<OrderRequest> batch : allBatches) {
            assertThat(batch)
                    .isNotEmpty()
                    .allSatisfy(
                            order -> {
                                assertThat(order.customerId()).isPositive();
                                assertThat(order.items())
                                        .isNotEmpty()
                                        .hasSize(2)
                                        .allSatisfy(
                                                item -> {
                                                    assertThat(item.productCode())
                                                            .startsWith("ProductCode");
                                                    assertThat(item.quantity())
                                                            .isBetween(1, 6); // RAND.nextInt(5) + 1
                                                });
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
                executorService.submit(() -> orderGeneratorService.generateOrders());
            }
        } finally {
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        }

        // Assert
        // Each thread will generate 10,000 orders in batches of 100
        int expectedBatchesPerThread = 10_000 / 100;
        verify(orderService, times(expectedBatchesPerThread * numThreads))
                .saveBatchOrders(anyList());
    }
}
