/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
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
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.repositories.OrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class OrderControllerIT extends AbstractIntegrationTest {

    @Autowired private OrderRepository orderRepository;

    private List<Order> orderList = null;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        orderList = new ArrayList<>();
        Order order1 = getOrder();
        this.orderList.add(order1);
        Order order2 = new Order();
        order2.setCustomerId(1L);
        order2.setStatus("NEW");
        this.orderList.add(order2);
        Order order3 = new Order();
        order3.setCustomerId(1L);
        order3.setStatus("NEW");
        OrderItem orderItem2 = new OrderItem();
        orderItem2.setProductCode("Product3");
        orderItem2.setQuantity(100);
        orderItem2.setProductPrice(BigDecimal.ONE);
        order3.addOrderItem(orderItem2);
        this.orderList.add(order3);

        orderList = orderRepository.saveAll(orderList);
    }

    private Order getOrder() {
        Order order = new Order();
        order.setCustomerId(1L);
        order.setStatus("NEW");
        OrderItem orderItem = new OrderItem();
        orderItem.setProductCode("Product1");
        orderItem.setQuantity(10);
        orderItem.setProductPrice(BigDecimal.TEN);
        OrderItem orderItem1 = new OrderItem();
        orderItem1.setProductCode("Product2");
        orderItem1.setQuantity(100);
        orderItem1.setProductPrice(BigDecimal.ONE);
        order.addOrderItem(orderItem);
        order.addOrderItem(orderItem1);
        return order;
    }

    @Test
    void shouldFetchAllOrders() throws Exception {
        this.mockMvc
                .perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(orderList.size())))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));
    }

    @Test
    void shouldFindOrderById() throws Exception {
        Order order = orderList.get(0);
        Long orderId = order.getId();

        this.mockMvc
                .perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(order.getCustomerId()), Long.class))
                .andExpect(jsonPath("$.status", is(order.getStatus())))
                .andExpect(jsonPath("$.items.size()", is(order.getItems().size())));
    }

    @Test
    void shouldCreateNewOrder() throws Exception {
        mockProductExistsRequest(true);
        OrderRequest orderRequest =
                new OrderRequest(1L, List.of(new OrderItemRequest("Product1", 10, BigDecimal.TEN)));

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.customerId", is(orderRequest.customerId()), Long.class))
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.items.size()", is(1)))
                .andExpect(jsonPath("$.items[0].itemId", notNullValue()))
                .andExpect(jsonPath("$.items[0].price", is(100)));
    }

    @Test
    void shouldReturn400WhenCreateNewOrderWithoutItems() throws Exception {
        OrderRequest orderRequest = new OrderRequest(-1L, new ArrayList<>());

        this.mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("about:blank")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/orders")))
                .andExpect(jsonPath("$.violations", hasSize(2)))
                .andExpect(jsonPath("$.violations[0].field", is("customerId")))
                .andExpect(jsonPath("$.violations[0].message", is("CustomerId should be positive")))
                .andExpect(jsonPath("$.violations[1].field", is("items")))
                .andExpect(jsonPath("$.violations[1].message", is("Order without items not valid")))
                .andReturn();
    }

    @Test
    void shouldUpdateOrder() throws Exception {
        mockProductsExistsRequest(true);
        Order order = orderList.get(0);

        OrderRequest orderDto = getOrderRequest(order);

        this.mockMvc
                .perform(
                        put("/api/orders/{id}", order.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.items[0].quantity", is(110)))
                .andExpect(jsonPath("$.items[0].price", is(1100)))
                .andExpect(jsonPath("$.items[1].quantity", is(100)))
                .andExpect(jsonPath("$.items[1].price", is(100)));
    }

    private OrderRequest getOrderRequest(Order order) {
        OrderItem orderItem = order.getItems().get(0);
        OrderItem orderItem1 = order.getItems().get(1);
        return new OrderRequest(
                order.getCustomerId(),
                List.of(
                        new OrderItemRequest(
                                orderItem.getProductCode(),
                                orderItem.getQuantity() + 100,
                                orderItem.getProductPrice()),
                        new OrderItemRequest(
                                orderItem1.getProductCode(),
                                orderItem1.getQuantity(),
                                orderItem1.getProductPrice()),
                        new OrderItemRequest(
                                "product4",
                                orderItem1.getQuantity(),
                                orderItem1.getProductPrice())));
    }

    @Test
    void shouldDeleteOrder() throws Exception {
        Order order = orderList.get(0);

        this.mockMvc
                .perform(delete("/api/orders/{id}", order.getId()))
                .andExpect(status().isAccepted());
    }
}
