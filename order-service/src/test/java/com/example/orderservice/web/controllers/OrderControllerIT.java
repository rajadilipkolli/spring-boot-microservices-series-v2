/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.model.Address;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.util.TestData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class OrderControllerIT extends AbstractIntegrationTest {

    private List<Order> orderList = null;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();

        orderList = new ArrayList<>();
        Order order1 = TestData.getOrder();
        this.orderList.add(order1);
        Order order2 =
                new Order()
                        .setCustomerId(1L)
                        .setStatus(OrderStatus.NEW)
                        .setDeliveryAddress(
                                new Address(
                                        "Junit Address11",
                                        "AddressLine12",
                                        "city2",
                                        "state2",
                                        "zipCode2",
                                        "country2"));
        this.orderList.add(order2);
        OrderItem orderItem =
                new OrderItem()
                        .setProductCode("Product3")
                        .setQuantity(100)
                        .setProductPrice(BigDecimal.ONE);
        Order order3 =
                new Order()
                        .setCustomerId(1L)
                        .setStatus(OrderStatus.NEW)
                        .setDeliveryAddress(
                                new Address(
                                        "Junit Address31",
                                        "AddressLine32",
                                        "city3",
                                        "state3",
                                        "zipCode3",
                                        "country3"));
        order3.addOrderItem(orderItem);
        this.orderList.add(order3);

        orderList = orderRepository.saveAll(orderList);
    }

    @Nested
    @DisplayName("find methods")
    class Find {

        @Test
        void shouldFetchAllOrders() throws Exception {
            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size()", is(8)))
                    .andExpect(jsonPath("$.data.size()", is(orderList.size())))
                    .andExpect(jsonPath("$.totalElements", is(3)))
                    .andExpect(jsonPath("$.pageNumber", is(1)))
                    .andExpect(jsonPath("$.totalPages", is(1)))
                    .andExpect(jsonPath("$.isFirst", is(true)))
                    .andExpect(jsonPath("$.isLast", is(true)))
                    .andExpect(jsonPath("$.hasNext", is(false)))
                    .andExpect(jsonPath("$.hasPrevious", is(false)))
                    .andExpect(
                            jsonPath(
                                    "$.data[0].items.size()",
                                    is(orderList.getFirst().getItems().size())));
        }

        @Test
        void shouldFindOrderById() throws Exception {
            Order order = orderList.getFirst();
            Long orderId = order.getId();

            mockMvc.perform(get("/api/orders/{id}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId", is(orderId), Long.class))
                    .andExpect(jsonPath("$.customerId", is(order.getCustomerId()), Long.class))
                    .andExpect(jsonPath("$.status", is(order.getStatus().name())))
                    .andExpect(jsonPath("$.source", is(order.getSource())))
                    .andExpect(
                            jsonPath(
                                    "$.deliveryAddress.addressLine1",
                                    is(order.getDeliveryAddress().addressLine1())))
                    .andExpect(
                            jsonPath(
                                    "$.deliveryAddress.addressLine2",
                                    is(order.getDeliveryAddress().addressLine2())))
                    .andExpect(
                            jsonPath(
                                    "$.deliveryAddress.city",
                                    is(order.getDeliveryAddress().city())))
                    .andExpect(
                            jsonPath(
                                    "$.deliveryAddress.state",
                                    is(order.getDeliveryAddress().state())))
                    .andExpect(
                            jsonPath(
                                    "$.deliveryAddress.zipCode",
                                    is(order.getDeliveryAddress().zipCode())))
                    .andExpect(
                            jsonPath(
                                    "$.deliveryAddress.country",
                                    is(order.getDeliveryAddress().country())))
                    .andExpect(jsonPath("$.totalPrice").value(closeTo(201.00, 0.01)))
                    .andExpect(jsonPath("$.items.size()", is(order.getItems().size())));
        }

        @Test
        void shouldReturn404WhenFetchingNonExistingOrder() throws Exception {
            Long orderId = 10_000L;
            mockMvc.perform(get("/api/orders/{id}", orderId))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            header().string(
                                            "Content-Type",
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type", is("https://api.microservices.com/errors/not-found")))
                    .andExpect(jsonPath("$.title", is("Order Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(
                            jsonPath("$.detail")
                                    .value("Order with Id %d not found".formatted(orderId)));
        }
    }

    @Nested
    @DisplayName("save methods")
    class SaveOrder {

        @Test
        void shouldCreateNewOrder() throws Exception {
            OrderRequest orderRequest =
                    new OrderRequest(
                            1L,
                            List.of(new OrderItemRequest("Product10", 10, BigDecimal.TEN)),
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"));
            mockProductsExistsRequest(true, "PRODUCT10");

            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(orderRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.orderId", notNullValue()))
                    .andExpect(jsonPath("$.customerId", is(orderRequest.customerId()), Long.class))
                    .andExpect(jsonPath("$.status", is("NEW")))
                    .andExpect(jsonPath("$.source", nullValue()))
                    .andExpect(jsonPath("$.totalPrice").value(closeTo(100.00, 0.01)))
                    .andExpect(jsonPath("$.items.size()", is(1)))
                    .andExpect(jsonPath("$.items[0].itemId", notNullValue()))
                    .andExpect(jsonPath("$.items[0].productId", is("Product10")))
                    .andExpect(jsonPath("$.items[0].quantity", is(10)))
                    .andExpect(jsonPath("$.items[0].productPrice").value(is(10)))
                    .andExpect(jsonPath("$.items[0].price").value(closeTo(100.00, 0.01)))
                    .andExpect(jsonPath("$.createdDate", notNullValue()))
                    // Verify address fields
                    .andExpect(jsonPath("$.deliveryAddress.addressLine1", is("Junit Address1")))
                    .andExpect(jsonPath("$.deliveryAddress.addressLine2", is("AddressLine2")))
                    .andExpect(jsonPath("$.deliveryAddress.city", is("city")))
                    .andExpect(jsonPath("$.deliveryAddress.state", is("state")))
                    .andExpect(jsonPath("$.deliveryAddress.zipCode", is("zipCode")))
                    .andExpect(jsonPath("$.deliveryAddress.country", is("country")));

            // Verify the order was actually saved in the database
            List<Order> savedOrders = orderRepository.findAll();
            assertThat(savedOrders)
                    .isNotEmpty()
                    .hasSize(orderList.size() + 1); // Original orders plus the new one

            // Verify the last order in the database matches our request
            savedOrders.sort(Comparator.comparing(Order::getId));
            Long orderId = savedOrders.getLast().getId();
            Order lastOrder = orderRepository.findOrderById(orderId).get();
            assertThat(lastOrder.getCustomerId()).isEqualTo(orderRequest.customerId());
            assertThat(lastOrder.getStatus().name()).isEqualTo("NEW");
            assertThat(lastOrder.getItems()).hasSize(1);
            assertThat(lastOrder.getItems().getFirst().getProductCode()).isEqualTo("Product10");
            assertThat(lastOrder.getItems().getFirst().getQuantity()).isEqualTo(10);
        }

        @Test
        void shouldCreateNewOrderFails() throws Exception {
            OrderRequest orderRequest =
                    new OrderRequest(
                            1L,
                            List.of(new OrderItemRequest("Product2", 10, BigDecimal.TEN)),
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"));
            mockProductsExistsRequest(false, "Product2");

            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(orderRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type", is("https://api.microservices.com/errors/not-found")))
                    .andExpect(jsonPath("$.title", is("Product Not Found")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(
                            jsonPath(
                                    "$.detail",
                                    is("One or More products Not found from [PRODUCT2]")))
                    .andExpect(jsonPath("$.instance", is("/api/orders")))
                    .andExpect(jsonPath("$.errorCategory").value("Generic"));
        }

        @Test
        void whenOrderWithInvalidAddress_shouldThrowValidationException() throws Exception {
            // Arrange
            OrderItemRequest validItem = new OrderItemRequest("ProductCode1", 1, BigDecimal.TEN);
            OrderRequest orderRequest =
                    new OrderRequest(1L, List.of(validItem), new Address("", "", "", "", "", ""));

            // Act & Assert
            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(orderRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type",
                                    is("https://api.microservices.com/errors/validation-error")))
                    .andExpect(jsonPath("$.title", is("Constraint Violation")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                    .andExpect(jsonPath("$.instance", is("/api/orders")))
                    .andExpect(jsonPath("$.violations", hasSize(5)))
                    .andExpect(
                            jsonPath("$.violations[0].field", is("deliveryAddress.addressLine1")))
                    .andExpect(jsonPath("$.violations[0].message", is("AddressLine1 is required")))
                    .andExpect(jsonPath("$.violations[1].field", is("deliveryAddress.city")))
                    .andExpect(jsonPath("$.violations[1].message", is("City is required")))
                    .andExpect(jsonPath("$.violations[2].field", is("deliveryAddress.country")))
                    .andExpect(jsonPath("$.violations[2].message", is("Country is required")))
                    .andExpect(jsonPath("$.violations[3].field", is("deliveryAddress.state")))
                    .andExpect(jsonPath("$.violations[3].message", is("State is required")))
                    .andExpect(jsonPath("$.violations[4].field", is("deliveryAddress.zipCode")))
                    .andExpect(jsonPath("$.violations[4].message", is("ZipCode is required")));
        }

        @Test
        void whenOrderWithEmptyItems_shouldThrowValidationException() throws Exception {
            // Arrange
            OrderRequest orderRequest =
                    new OrderRequest(
                            1L,
                            Collections.emptyList(),
                            new Address(
                                    "123 Street", "Apt 1", "City", "State", "12345", "Country"));

            // Act & Assert
            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(orderRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            header().string(
                                            HttpHeaders.CONTENT_TYPE,
                                            is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                    .andExpect(
                            jsonPath(
                                    "$.type",
                                    is("https://api.microservices.com/errors/validation-error")))
                    .andExpect(jsonPath("$.title", is("Constraint Violation")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                    .andExpect(jsonPath("$.instance", is("/api/orders")))
                    .andExpect(jsonPath("$.violations", hasSize(1)))
                    .andExpect(jsonPath("$.violations[0].field", is("items")))
                    .andExpect(
                            jsonPath(
                                    "$.violations[0].message",
                                    is("Order without items not valid")));
        }

        @Test
        void shouldReturn400WhenCreateNewOrderWithoutItems() throws Exception {
            OrderRequest orderRequest =
                    new OrderRequest(
                            -1L,
                            new ArrayList<>(),
                            new Address(
                                    "Junit Address1",
                                    "AddressLine2",
                                    "city",
                                    "state",
                                    "zipCode",
                                    "country"));

            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(orderRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", is("application/problem+json")))
                    .andExpect(
                            jsonPath(
                                    "$.type",
                                    is("https://api.microservices.com/errors/validation-error")))
                    .andExpect(jsonPath("$.title", is("Constraint Violation")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                    .andExpect(jsonPath("$.instance", is("/api/orders")))
                    .andExpect(jsonPath("$.violations", hasSize(2)))
                    .andExpect(jsonPath("$.violations[0].field", is("customerId")))
                    .andExpect(
                            jsonPath(
                                    "$.violations[0].message", is("CustomerId should be positive")))
                    .andExpect(jsonPath("$.violations[1].field", is("items")))
                    .andExpect(
                            jsonPath(
                                    "$.violations[1].message", is("Order without items not valid")))
                    .andReturn();
        }

        @Test
        void shouldReturn400WhenOrderItemValidationFails() throws Exception {
            // Test invalid productCode (blank)
            OrderRequest invalidProductCodeRequest =
                    new OrderRequest(
                            1L,
                            List.of(new OrderItemRequest("", 2, new BigDecimal("10.00"))),
                            new Address("Line1", "Line2", "City", "State", "12345", "Country"));

            mockProductsExistsRequest(true, "");
            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            jsonMapper.writeValueAsString(
                                                    invalidProductCodeRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", is("application/problem+json")))
                    .andExpect(
                            jsonPath(
                                    "$.type",
                                    is("https://api.microservices.com/errors/validation-error")))
                    .andExpect(jsonPath("$.title", is("Constraint Violation")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                    .andExpect(jsonPath("$.instance", is("/api/orders")))
                    .andExpect(jsonPath("$.violations[0].field", is("items[0].productCode")))
                    .andExpect(
                            jsonPath(
                                    "$.violations[0].message",
                                    is("Product code must be provided")));

            // Test invalid quantity (zero)
            OrderRequest invalidQuantityRequest =
                    new OrderRequest(
                            1L,
                            List.of(new OrderItemRequest("Product1", 0, new BigDecimal("10.00"))),
                            new Address("Line1", "Line2", "City", "State", "12345", "Country"));

            mockProductsExistsRequest(true, "Product1");
            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(invalidQuantityRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", is("application/problem+json")))
                    .andExpect(
                            jsonPath(
                                    "$.type",
                                    is("https://api.microservices.com/errors/validation-error")))
                    .andExpect(jsonPath("$.title", is("Constraint Violation")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                    .andExpect(jsonPath("$.instance", is("/api/orders")))
                    .andExpect(jsonPath("$.violations[0].field", is("items[0].quantity")))
                    .andExpect(
                            jsonPath(
                                    "$.violations[0].message",
                                    is("Quantity should be greater than zero")));

            // Test invalid price (zero)
            OrderRequest invalidPriceRequest =
                    new OrderRequest(
                            1L,
                            List.of(new OrderItemRequest("Product1", 2, new BigDecimal("0.00"))),
                            new Address("Line1", "Line2", "City", "State", "12345", "Country"));

            mockProductsExistsRequest(true, "Product1");
            mockMvc.perform(
                            post("/api/orders")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonMapper.writeValueAsString(invalidPriceRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", is("application/problem+json")))
                    .andExpect(
                            jsonPath(
                                    "$.type",
                                    is("https://api.microservices.com/errors/validation-error")))
                    .andExpect(jsonPath("$.title", is("Constraint Violation")))
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                    .andExpect(jsonPath("$.instance", is("/api/orders")))
                    .andExpect(jsonPath("$.violations[0].field", is("items[0].productPrice")))
                    .andExpect(
                            jsonPath(
                                    "$.violations[0].message",
                                    is("Price should be greater than zero")));
        }
    }

    @Test
    void shouldUpdateOrder() throws Exception {
        mockProductsExistsRequest(true, "product1", "product4");
        Order order = orderList.getFirst();

        OrderRequest orderRequest = TestData.getOrderRequest(order);

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", order.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.totalPrice").value(closeTo(1211.00, 0.01)))
                .andExpect(jsonPath("$.items.size()", is(2)))
                .andExpect(jsonPath("$.items[0].quantity", is(110)))
                .andExpect(jsonPath("$.items[0].price", is(1111.00)))
                .andExpect(jsonPath("$.items[1].quantity", is(100)))
                .andExpect(jsonPath("$.items[1].price", is(100.00)))
                .andExpect(
                        jsonPath(
                                "$.deliveryAddress.addressLine1",
                                is(orderRequest.deliveryAddress().addressLine1())))
                .andExpect(
                        jsonPath(
                                "$.deliveryAddress.addressLine2",
                                is(orderRequest.deliveryAddress().addressLine2())))
                .andExpect(
                        jsonPath(
                                "$.deliveryAddress.city",
                                is(orderRequest.deliveryAddress().city())))
                .andExpect(
                        jsonPath(
                                "$.deliveryAddress.state",
                                is(orderRequest.deliveryAddress().state())))
                .andExpect(
                        jsonPath(
                                "$.deliveryAddress.zipCode",
                                is(orderRequest.deliveryAddress().zipCode())))
                .andExpect(
                        jsonPath(
                                "$.deliveryAddress.country",
                                is(orderRequest.deliveryAddress().country())));
    }

    @Test
    void shouldDeleteOrder() throws Exception {
        Order order = orderList.getFirst();

        this.mockMvc
                .perform(delete("/api/orders/{id}", order.getId()))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldFindOrdersByCustomersId() throws Exception {
        OrderItem orderItem = orderList.getFirst().getItems().getFirst();
        mockMvc.perform(
                        get("/api/orders/customer/{id}", orderList.getFirst().getCustomerId())
                                .queryParam("page", "0")
                                .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(orderList.size())))
                .andExpect(
                        jsonPath(
                                "$.data[0].customerId",
                                is(orderList.getFirst().getCustomerId()),
                                Long.class))
                .andExpect(
                        jsonPath(
                                "$.data[0].items[0].price",
                                is(
                                        orderItem
                                                .getProductPrice()
                                                .multiply(new BigDecimal(orderItem.getQuantity()))),
                                BigDecimal.class));
    }

    @Test
    void shouldPreserveOrderStructureAfterUpdate() throws Exception {
        // Get an order from the existing list
        Order order = orderList.getFirst();
        Long orderId = order.getId();

        // Create a new request for update with modified data
        OrderRequest updateRequest =
                new OrderRequest(
                        1L,
                        List.of(
                                new OrderItemRequest(
                                        "UpdatedProduct", 15, BigDecimal.valueOf(12.99)),
                                new OrderItemRequest("SecondProduct", 5, BigDecimal.valueOf(7.50))),
                        new Address(
                                "Updated Address",
                                "Suite 123",
                                "New City",
                                "New State",
                                "54321",
                                "New Country"));

        mockProductsExistsRequest(true, "UpdatedProduct", "SecondProduct");

        // Perform the update
        mockMvc.perform(
                        put("/api/orders/{id}", orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is(orderId), Long.class))
                .andExpect(jsonPath("$.customerId", is(1)))
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.deliveryAddress.addressLine1", is("Updated Address")))
                .andExpect(jsonPath("$.deliveryAddress.addressLine2", is("Suite 123")))
                .andExpect(jsonPath("$.deliveryAddress.city", is("New City")))
                .andExpect(jsonPath("$.deliveryAddress.state", is("New State")))
                .andExpect(jsonPath("$.deliveryAddress.zipCode", is("54321")))
                .andExpect(jsonPath("$.deliveryAddress.country", is("New Country")))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productId", is("UpdatedProduct")))
                .andExpect(jsonPath("$.items[0].quantity", is(15)))
                .andExpect(jsonPath("$.items[0].productPrice").value(closeTo(12.99, 0.01)))
                .andExpect(jsonPath("$.items[0].price").value(closeTo(194.85, 0.01)))
                .andExpect(jsonPath("$.items[1].productId", is("SecondProduct")))
                .andExpect(jsonPath("$.items[1].quantity", is(5)))
                .andExpect(jsonPath("$.items[1].productPrice").value(closeTo(7.50, 0.01)))
                .andExpect(jsonPath("$.items[1].price").value(closeTo(37.50, 0.01)))
                .andExpect(jsonPath("$.totalPrice").value(closeTo(232.35, 0.01)))
                .andExpect(jsonPath("$.createdDate", notNullValue()));

        // Verify that the database was updated properly
        Order updatedOrder = orderRepository.findOrderById(orderId).orElseThrow();
        assertThat(updatedOrder.getDeliveryAddress().addressLine1()).isEqualTo("Updated Address");
        assertThat(updatedOrder.getDeliveryAddress().city()).isEqualTo("New City");
        assertThat(updatedOrder.getItems()).hasSize(2);

        // Verify items are updated properly
        boolean foundUpdatedProduct = false;
        boolean foundSecondProduct = false;

        for (OrderItem item : updatedOrder.getItems()) {
            if (item.getProductCode().equals("UpdatedProduct")) {
                foundUpdatedProduct = true;
                assertThat(item.getQuantity()).isEqualTo(15);
                assertThat(item.getProductPrice()).isEqualByComparingTo(BigDecimal.valueOf(12.99));
            } else if (item.getProductCode().equals("SecondProduct")) {
                foundSecondProduct = true;
                assertThat(item.getQuantity()).isEqualTo(5);
                assertThat(item.getProductPrice()).isEqualByComparingTo(BigDecimal.valueOf(7.50));
            }
        }

        assertThat(foundUpdatedProduct).isTrue();
        assertThat(foundSecondProduct).isTrue();
    }
}
