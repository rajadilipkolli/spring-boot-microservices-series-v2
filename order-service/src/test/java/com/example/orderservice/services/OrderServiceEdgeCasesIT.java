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
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrderServiceEdgeCasesIT extends AbstractIntegrationTest {

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

        // Assert basic response
        assertThat(responses).hasSize(1);
        OrderResponse response = responses.getFirst();
        assertThat(response.items()).hasSize(2);

        // Verify detailed response structure
        assertThat(response.orderId()).isNotNull();
        assertThat(response.customerId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.source()).isNull();
        assertThat(response.createdDate()).isNotNull();

        // Verify order items details
        assertThat(response.items()).extracting("productId").containsOnly("PROD1");

        assertThat(response.items()).extracting("quantity").containsExactlyInAnyOrder(1, 2);

        // Verify price calculations
        assertThat(response.totalPrice())
                .isEqualByComparingTo(BigDecimal.valueOf(30)); // 10*1 + 10*2

        // Verify delivery address is preserved
        assertThat(response.deliveryAddress()).isNotNull();
        assertThat(response.deliveryAddress().addressLine1()).isEqualTo("addr1");
        assertThat(response.deliveryAddress().addressLine2()).isEqualTo("addr2");
        assertThat(response.deliveryAddress().city()).isEqualTo("city");
        assertThat(response.deliveryAddress().state()).isEqualTo("state");
        assertThat(response.deliveryAddress().zipCode()).isEqualTo("zip");
        assertThat(response.deliveryAddress().country()).isEqualTo("country");
    }

    @Test
    void saveBatchOrders_WithConcurrentRequests_ShouldHandleConcurrencyCorrectly()
            throws InterruptedException {
        // Arrange
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch(); // Clear existing orders

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
                            List<OrderResponse> responses = orderService.saveBatchOrders(requests);

                            // Verify responses match requests in size
                            assertThat(responses).hasSize(requests.size());

                            // Verify each response has a valid order ID
                            assertThat(responses)
                                    .allSatisfy(
                                            response ->
                                                    assertThat(response.orderId())
                                                            .isNotNull()
                                                            .isPositive());

                            // Verify status is NEW for all orders
                            assertThat(responses)
                                    .allSatisfy(
                                            response ->
                                                    assertThat(response.status()).isEqualTo("NEW"));
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        assertThat(completed)
                .isTrue()
                .as("All threads should complete within timeout"); // Verify all threads completed
        long totalOrders = orderRepository.count();
        assertThat(totalOrders)
                .as("Expected exactly %d orders", numThreads * ordersPerThread)
                .isEqualTo(numThreads * ordersPerThread);

        // Verify order structures
        List<OrderResponse> allOrders = orderService.findAllOrders(0, 200, "id", "asc").data();

        assertThat(allOrders)
                .hasSize(numThreads * ordersPerThread)
                .allSatisfy(
                        order -> {
                            assertThat(order.customerId()).isEqualTo(1L);
                            assertThat(order.status()).isEqualTo("NEW");
                            assertThat(order.items()).hasSize(1);
                            assertThat(order.items().getFirst().productId()).isEqualTo("PROD1");
                            assertThat(order.items().getFirst().quantity()).isEqualTo(1);
                            assertThat(order.deliveryAddress().addressLine1()).isEqualTo("addr1");
                        });
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

        // Verify order properties
        assertThat(response.orderId()).isNotNull().isPositive();
        assertThat(response.customerId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("NEW");
        assertThat(response.source()).isNull();
        assertThat(response.createdDate()).isNotNull();
        assertThat(response.deliveryAddress()).isNotNull();

        // Verify order items
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().quantity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(response.items().getFirst().productId()).isEqualTo("PROD1");
        assertThat(response.items().getFirst().productPrice()).isEqualByComparingTo(BigDecimal.TEN);

        // Calculate expected total price (MAX_VALUE * 10) and verify
        BigDecimal expectedTotal = BigDecimal.valueOf(Integer.MAX_VALUE).multiply(BigDecimal.TEN);
        assertThat(response.totalPrice()).isEqualByComparingTo(expectedTotal);

        // Verify item price equals item subtotal
        assertThat(response.items().getFirst().price()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void whenOrderWithNonExistentProduct_shouldThrowProductNotFoundException() {
        // Arrange
        long initialCount = orderRepository.count();
        OrderItemRequest invalidItem =
                new OrderItemRequest("NonExistentProduct", 1, BigDecimal.TEN);
        OrderRequest request =
                new OrderRequest(
                        1L,
                        List.of(invalidItem),
                        new Address("123 Street", "Apt 1", "City", "State", "12345", "Country"));

        // Mock the product existence check to return false
        mockProductsExistsRequest(false, "NonExistentProduct"); // Act & Assert
        assertThatThrownBy(() -> orderService.saveOrder(request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("NONEXISTENTPRODUCT")
                .satisfies(
                        exception -> {
                            ProductNotFoundException e = (ProductNotFoundException) exception;
                            // Verify the exception details
                            ProblemDetail problemDetail = e.getBody();
                            assertThat(problemDetail).isNotNull();
                            assertThat(problemDetail.getDetail())
                                    .containsIgnoringCase("NonExistentProduct");
                            assertThat(problemDetail.getTitle()).isEqualTo("Product Not Found");
                        });

        // Verify no order was saved
        assertThat(orderRepository.count() - initialCount).isZero();
    }

    @Test
    void whenUpdateOrderWithNullEntity_shouldThrowNullPointerException() {
        long initialCount = orderRepository.count();
        // Arrange
        OrderRequest request =
                new OrderRequest(
                        1L,
                        List.of(new OrderItemRequest("ProductCode1", 1, BigDecimal.TEN)),
                        new Address("123 Street", "Apt 1", "City", "State", "12345", "Country"));

        mockProductsExistsRequest(true, "ProductCode1");

        // Act & Assert
        assertThatThrownBy(() -> orderService.updateOrder(request, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("\"order\" is null");

        // Verify no order was saved
        assertThat(orderRepository.count() - initialCount).isZero();
    }

    @Test
    void saveOrder_WithMultipleDistinctProducts_ShouldSucceed() {
        // Arrange
        List<OrderItemRequest> items =
                List.of(
                        new OrderItemRequest("PROD1", 2, BigDecimal.valueOf(10.99)),
                        new OrderItemRequest("PROD2", 1, BigDecimal.valueOf(24.99)),
                        new OrderItemRequest("PROD3", 5, BigDecimal.valueOf(5.49)));

        OrderRequest request =
                new OrderRequest(
                        1L,
                        items,
                        new Address("addr1", "addr2", "city", "state", "zip", "country"));

        mockProductsExistsRequest(true, "PROD1", "PROD2", "PROD3");

        // Act
        OrderResponse response = orderService.saveOrder(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isNotNull().isPositive();
        assertThat(response.customerId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("NEW");

        // Verify items were processed correctly
        assertThat(response.items()).hasSize(3);

        // Calculate expected total price: (2 * 10.99) + (1 * 24.99) + (5 * 5.49) = 21.98 + 24.99 +
        // 27.45 = 74.42
        BigDecimal expectedTotal =
                BigDecimal.valueOf(74.42).setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(response.totalPrice()).isEqualByComparingTo(expectedTotal);

        // Verify each product in the response
        boolean foundProd1 = false;
        boolean foundProd2 = false;
        boolean foundProd3 = false;

        for (var item : response.items()) {
            if ("PROD1".equals(item.productId())) {
                assertThat(item.quantity()).isEqualTo(2);
                assertThat(item.productPrice()).isEqualByComparingTo(BigDecimal.valueOf(10.99));
                assertThat(item.price()).isEqualByComparingTo(BigDecimal.valueOf(21.98));
                foundProd1 = true;
            } else if ("PROD2".equals(item.productId())) {
                assertThat(item.quantity()).isEqualTo(1);
                assertThat(item.productPrice()).isEqualByComparingTo(BigDecimal.valueOf(24.99));
                assertThat(item.price()).isEqualByComparingTo(BigDecimal.valueOf(24.99));
                foundProd2 = true;
            } else if ("PROD3".equals(item.productId())) {
                assertThat(item.quantity()).isEqualTo(5);
                assertThat(item.productPrice()).isEqualByComparingTo(BigDecimal.valueOf(5.49));
                assertThat(item.price()).isEqualByComparingTo(BigDecimal.valueOf(27.45));
                foundProd3 = true;
            }
        }

        assertThat(foundProd1).isTrue();
        assertThat(foundProd2).isTrue();
        assertThat(foundProd3).isTrue();
    }

    @Test
    void updateOrder_WithCompletelyNewItems_ShouldReplaceAllItems() {
        // Arrange - Create an initial order with specific items
        OrderRequest initialRequest =
                new OrderRequest(
                        1L,
                        List.of(new OrderItemRequest("PROD1", 2, BigDecimal.TEN)),
                        new Address("addr1", "addr2", "city", "state", "zip", "country"));

        mockProductsExistsRequest(true, "PROD1", "PROD2", "PROD3");

        // Save the initial order
        OrderResponse initialOrder = orderService.saveOrder(initialRequest);
        assertThat(initialOrder.orderId()).isNotNull();
        assertThat(initialOrder.items()).hasSize(1);
        assertThat(initialOrder.items().getFirst().productId()).isEqualTo("PROD1");

        // Create update request with completely new items
        OrderRequest updateRequest =
                new OrderRequest(
                        1L,
                        List.of(
                                new OrderItemRequest("PROD2", 3, BigDecimal.valueOf(15)),
                                new OrderItemRequest("PROD3", 1, BigDecimal.valueOf(25))),
                        new Address(
                                "new-addr1",
                                "new-addr2",
                                "new-city",
                                "new-state",
                                "new-zip",
                                "new-country"));

        // Find the order entity for updating
        var orderToUpdate = orderRepository.findById(initialOrder.orderId()).orElseThrow();

        // Act
        OrderResponse updatedOrder = orderService.updateOrder(updateRequest, orderToUpdate);

        // Assert
        assertThat(updatedOrder).isNotNull();
        assertThat(updatedOrder.orderId()).isEqualTo(initialOrder.orderId());
        assertThat(updatedOrder.customerId()).isEqualTo(1L);

        // Original item should be gone, replaced by the new ones
        assertThat(updatedOrder.items()).hasSize(2);
        assertThat(updatedOrder.items())
                .extracting("productId")
                .containsExactlyInAnyOrder("PROD2", "PROD3")
                .doesNotContain("PROD1");

        // Address should be updated
        assertThat(updatedOrder.deliveryAddress().addressLine1()).isEqualTo("new-addr1");
        assertThat(updatedOrder.deliveryAddress().city()).isEqualTo("new-city");

        // Verify prices are calculated correctly
        BigDecimal expectedTotal = BigDecimal.valueOf(3 * 15 + 1 * 25);
        assertThat(updatedOrder.totalPrice()).isEqualByComparingTo(expectedTotal);

        // Verify the database was also updated
        OrderResponse refreshedOrder =
                orderService.findOrderByIdAsResponse(updatedOrder.orderId()).orElseThrow();
        assertThat(refreshedOrder.items()).hasSize(2);
        assertThat(refreshedOrder.items())
                .extracting("productId")
                .containsExactlyInAnyOrder("PROD2", "PROD3");
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
