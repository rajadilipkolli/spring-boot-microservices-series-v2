/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.model.Address;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.OrderResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("performance")
class OrderServicePerformanceIT extends AbstractIntegrationTest {

    private static final int BATCH_SIZE = 1000;
    private static final int TOTAL_ORDERS = 10000;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    void bulkOrderCreation_ShouldMeetPerformanceTarget() {
        // Arrange
        List<OrderRequest> orders = generateLargeOrderBatch(BATCH_SIZE);
        mockProductsExistsRequest(true, "PERF-PROD");

        // Act
        Instant start = Instant.now();
        List<OrderResponse> responses = orderService.saveBatchOrders(orders);
        Duration duration = Duration.between(start, Instant.now());

        // Assert
        assertThat(responses).hasSize(BATCH_SIZE);
        assertThat(duration).isLessThan(Duration.ofSeconds(10)); // Performance target
    }

    @Test
    void orderRetrieval_ShouldMeetPerformanceTarget() {
        // Arrange
        insertTestOrders(TOTAL_ORDERS);
        mockProductsExistsRequest(true, "PERF-PROD");

        // Act
        Instant start = Instant.now();
        List<OrderResponse> responses = orderService.findAllOrders(0, 100, "id", "asc").data();
        Duration duration = Duration.between(start, Instant.now());

        // Assert
        assertThat(responses).isNotEmpty();
        assertThat(duration).isLessThan(Duration.ofSeconds(2)); // Performance target for retrieval
    }

    @Test
    void batchProcessing_ShouldScaleLinearly() {
        // Test different batch sizes to verify linear scaling
        int[] batchSizes = {100, 500, 1000};
        mockProductsExistsRequest(true, "PERF-PROD");

        for (int size : batchSizes) {
            List<OrderRequest> orders = generateLargeOrderBatch(size);

            // Measure processing time
            Instant start = Instant.now();
            List<OrderResponse> responses = orderService.saveBatchOrders(orders);
            Duration duration = Duration.between(start, Instant.now());

            // Assert batch completion and rough linear scaling
            assertThat(responses).hasSize(size);
            // Assuming linear scaling, time per item should be relatively constant
            long timePerItem = duration.toMillis() / size;
            assertThat(timePerItem).isLessThan(10); // Max 10ms per item
        }
    }

    @Test
    void largeOrderWithManyProducts_ShouldProcessEfficiently() {
        // Arrange - Create an order with many different products
        int productCount = 50;
        List<OrderItemRequest> items =
                IntStream.range(0, productCount)
                        .mapToObj(
                                i ->
                                        new OrderItemRequest(
                                                "PERF-PROD-" + i,
                                                i + 1, // quantity increases with product number
                                                BigDecimal.valueOf(
                                                        5.0 + (i * 0.5)) // price increases
                                                // with product
                                                // number
                                                ))
                        .toList();

        OrderRequest request =
                new OrderRequest(
                        1L,
                        items,
                        new Address(
                                "Performance Test Address",
                                "Line 2",
                                "Test City",
                                "Test State",
                                "12345",
                                "Test Country"));

        // Set up mock to accept all these product codes
        String[] productCodes =
                IntStream.range(0, productCount)
                        .mapToObj(i -> "PERF-PROD-" + i)
                        .toArray(String[]::new);
        mockProductsExistsRequest(true, productCodes);

        // Act
        Instant start = Instant.now();
        OrderResponse response = orderService.saveOrder(request);
        Duration duration = Duration.between(start, Instant.now());

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(productCount);

        // Verify processing time is reasonable
        assertThat(duration).isLessThan(Duration.ofSeconds(2));

        // Verify total price calculation is correct
        double expectedTotal =
                IntStream.range(0, productCount)
                        .mapToDouble(i -> (i + 1) * (5.0 + (i * 0.5)))
                        .sum();

        assertThat(response.totalPrice().doubleValue()).isCloseTo(expectedTotal, offset(0.01));

        // Verify all products are properly saved
        for (int i = 0; i < productCount; i++) {
            String productCode = "PERF-PROD-" + i;
            int expectedQuantity = i + 1;
            BigDecimal expectedPrice = BigDecimal.valueOf(5.0 + (i * 0.5));
            assertThat(response.items())
                    .anyMatch(
                            item ->
                                    item.productId().equals(productCode)
                                            && item.quantity() == expectedQuantity
                                            && item.productPrice().compareTo(expectedPrice) == 0);
        }
    }

    private List<OrderRequest> generateLargeOrderBatch(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        i ->
                                new OrderRequest(
                                        1L,
                                        List.of(
                                                new OrderItemRequest(
                                                        "PERF-PROD", 1, BigDecimal.valueOf(9.99))),
                                        new Address(
                                                "Performance Test Address",
                                                "Line 2",
                                                "Test City",
                                                "Test State",
                                                "12345",
                                                "Test Country")))
                .toList();
    }

    private void insertTestOrders(int count) {
        List<OrderRequest> orders = generateLargeOrderBatch(count);
        orderService.saveBatchOrders(orders);
    }
}
