/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.exception.ProductNotFoundException;
import com.example.orderservice.model.Address;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.repositories.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrderServiceEdgeCasesIT extends AbstractIntegrationTest {

    @Autowired private OrderService orderService;

    @Autowired private OrderRepository orderRepository;

    @Test
    void saveBatchOrders_WithDuplicateProductCodes_ShouldSaveAllOrders() {
        // Arrange
        List<OrderItemRequest> items =
                List.of(
                        new OrderItemRequest("PROD1", 1, BigDecimal.TEN),
                        new OrderItemRequest("PROD1", 2, BigDecimal.TEN));

        OrderRequest request =
                new OrderRequest(
                        1L,
                        items,
                        new Address("addr1", "addr2", "city", "state", "zip", "country"));

        mockProductsExistsRequest(true, "PROD1");

        // Act
        List<OrderResponse> responses = orderService.saveBatchOrders(List.of(request));

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().items()).hasSize(2);
    }

    @Test
    void saveBatchOrders_WithConcurrentRequests_ShouldHandleConcurrencyCorrectly()
            throws InterruptedException {
        // Arrange
        orderRepository.deleteAll(); // Clear existing orders

        int numThreads = 5;
        int ordersPerThread = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        mockProductsExistsRequest(true, "PROD1");

        // Act
        for (int i = 0; i < numThreads; i++) {
            executorService.submit(
                    () -> {
                        try {
                            List<OrderRequest> requests = generateOrderRequests(ordersPerThread);
                            orderService.saveBatchOrders(requests);
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        assertThat(completed).isTrue(); // Verify all threads completed
        long totalOrders = orderRepository.count();
        assertThat(totalOrders)
                .as("Expected exactly %d orders", numThreads * ordersPerThread)
                .isEqualTo(numThreads * ordersPerThread);
    }

    @Test
    @Transactional
    void saveOrder_WithMaxQuantity_ShouldSucceed() {
        // Arrange
        OrderItemRequest item = new OrderItemRequest("PROD1", Integer.MAX_VALUE, BigDecimal.TEN);
        OrderRequest request =
                new OrderRequest(
                        1L,
                        List.of(item),
                        new Address("addr1", "addr2", "city", "state", "zip", "country"));

        mockProductsExistsRequest(true, "PROD1");

        // Act
        OrderResponse response = orderService.saveOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.items().getFirst().quantity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void whenOrderWithNonExistentProduct_shouldThrowProductNotFoundException() {
        // Arrange
        OrderItemRequest invalidItem =
                new OrderItemRequest("NonExistentProduct", 1, BigDecimal.TEN);
        OrderRequest request =
                new OrderRequest(
                        1L,
                        List.of(invalidItem),
                        new Address("123 Street", "Apt 1", "City", "State", "12345", "Country"));

        // Act & Assert
        assertThatThrownBy(() -> orderService.saveOrder(request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void whenUpdateOrderWithNullEntity_shouldThrowNullPointerException() {
        // Arrange
        OrderRequest request =
                new OrderRequest(
                        1L,
                        List.of(new OrderItemRequest("ProductCode1", 1, BigDecimal.TEN)),
                        new Address("123 Street", "Apt 1", "City", "State", "12345", "Country"));

        // Act & Assert
        assertThatThrownBy(() -> orderService.updateOrder(request, null))
                .isInstanceOf(NullPointerException.class);
    }

    private List<OrderRequest> generateOrderRequests(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        i ->
                                new OrderRequest(
                                        1L,
                                        List.of(new OrderItemRequest("PROD1", 1, BigDecimal.TEN)),
                                        new Address(
                                                "addr1", "addr2", "city", "state", "zip",
                                                "country")))
                .toList();
    }
}
